package tas.probing

import tas.service.{
  ConnectionHandle,
  SocketConnectionHandle
}
import tas.service.Service
import tas.service.Address
import tas.service.AddressByName
import tas.concurrency.RunLoop
import scala.annotation.tailrec

import tas.types.{
  Interval,
  Time
}
import tas.utils.IO

import tas.probing.running.Protocol
import scala.collection.mutable.ListBuffer

private [probing] object ProbeController {
  class CantStartService extends Exception("cant start service")

  class Task[ConfigType <: Serializable](val logFilePath:String,
                                         val prefixPostfixLines:List[String],
                                         val config:ConfigType)

  trait TasksProvider[ConfigType <: Serializable] {
    def count:Int
    def skip:Int
    def nextTask:Option[ProbeController.Task[ConfigType]]
  } 
}


private final class RunnerTiming {
  private val SKIP_FIRST_RUNS = 2
  private val RUNS_TO_CALCULATE_LONGEST = 4
  private val MAX_RUNS_TO_CALCULATE_LONGEST = 20

  private var _runStartTime:Long = 0

  private var _longestRunTimes = new ListBuffer[Long]
  private var _lastTime:Long = 0
  private var _skippedFirstRuns = 0
  
  def taskStarted = {
    _runStartTime = System.currentTimeMillis
  }

  def taskEnded = {
    _lastTime = (System.currentTimeMillis - _runStartTime)

    if (_skippedFirstRuns < SKIP_FIRST_RUNS) {
      _skippedFirstRuns += 1
    } else {
      _longestRunTimes += _lastTime
      _longestRunTimes = _longestRunTimes.sortWith(_ > _)
      while (_longestRunTimes.size > MAX_RUNS_TO_CALCULATE_LONGEST) {
        val indexOfLast = _longestRunTimes.size - 1
        _longestRunTimes.remove(indexOfLast)
      }
    } 
  }

  def last = _lastTime

  def longestMs:Option[Long] = {
    if (_skippedFirstRuns >= SKIP_FIRST_RUNS
        && _longestRunTimes.size >= RUNS_TO_CALCULATE_LONGEST) {
      Some(_longestRunTimes.sum / _longestRunTimes.size)
    } else None
  } 
} 

private [probing]
final class ProbeController[ConfigType <: Serializable](tasksProvider:ProbeController.TasksProvider[ConfigType]) {

  import ProbeController.Task
  
  private val addressString = "0.0.0.0"
  private val portRange = (9000 to 9100).toList

  private val runLoop = new RunLoop

  private val _runnerHandles = new ListBuffer[RunnerHandle]
  private val _pendingTasks = new ListBuffer[Task[ConfigType]]
  private var _completedTasks = 0

  private val startTime = Time.now

  tas.utils.repeat(tasksProvider.skip) {
    tasksProvider.nextTask
  }
  
  private class RunnerHandle(connection:ConnectionHandle) {

    private var _terminated = false
    private var _currentTask:Option[Task[ConfigType]] = None
    private val _timing = new RunnerTiming

    def onIncomingPacket(array:Array[Byte]) = {
      Protocol.readPacket(array) match {
        case packet:Protocol.ClientToServerPacket => packet match {
          case Protocol.TaskCompleted() => onRunnerCompletedTask()
        }
        case _:Protocol.ServerToClientPacket => throw new RuntimeException("client sending server->client packet!")
      }
    }

    connection.setHandlers(onPacket = onIncomingPacket _,
                           onDisconnect = onRunnerLost _)

    private def onRunnerCompletedTask() = {
      _timing.taskEnded
      _currentTask = None

      _completedTasks += 1

      val expectation = calculateExpectation()
      val expectedString = { if (expectation == None) ""
                             else (" expected: " + Interval.milliseconds(expectation.get)) }

      val countString = tasksProvider.count.toString
      val completedString = (_completedTasks + tasksProvider.skip).toString
                             
      println("ended "
                + (" " * (countString.length - completedString.length))
                + completedString + " of " + countString
                + " (" + Interval.milliseconds(_timing.last) + ")"
                + " elapsed: " + (Time.now - startTime)
                + expectedString)
  
      dispatchTasks()
    }

    private def onRunnerLost() = {
      if (! _terminated) {
        println("runner lost")
      }

      if (_currentTask != None) {
        _pendingTasks += _currentTask.get
        _runnerHandles -= this
        dispatchTasks()
      } 
    }

    def isIdle = _currentTask == None
    def longestMs = _timing.longestMs

    def startTask(task:Task[ConfigType]) = {
      _timing.taskStarted
      _currentTask = Some(task)
      connection.sendRawData(Protocol.writePacket(new Protocol.RunTask(task.logFilePath,
                                                                       task.prefixPostfixLines,
                                                                       task.config)))
    }

    def terminate = {
      _terminated = true
      connection.sendRawData(Protocol.writePacket(new Protocol.Terminate()))
      connection.close()
    }
  }

  private def calculateExpectation():Option[Long] = {
    val longests = _runnerHandles.map(_.longestMs)

    if (longests.exists(_.getOrElse(0) == 0)) None
    else {

      val timeToCompleteRunByAllRunners = longests.map(1 / _.get.asInstanceOf[Double]).sum
      val runsLeft = (tasksProvider.count - _completedTasks - tasksProvider.skip).asInstanceOf[Double]

      Some((runsLeft / timeToCompleteRunByAllRunners).asInstanceOf[Long])
    } 
  
  } 

  private def onNewConnection(connection:ConnectionHandle) = {
    println("runner connected")
    _runnerHandles += new RunnerHandle(connection)
    dispatchTasks()
  }

  private def dispatchTasks() = {
    if (_runnerHandles.size == 0) {
      println("all runners disconnected, terminating")

      if (! _pendingTasks.isEmpty) println("Warning: probing not completed!")

      postRunLoopExit()
    } else {

      @tailrec def setTaskToNextIdleRunner:Unit = {
        val idleRunner = _runnerHandles.find(_.isIdle)
        if (idleRunner.isDefined) {
          val nextTask = if (_pendingTasks.isEmpty) tasksProvider.nextTask
                         else Some(_pendingTasks.remove(0))
          
          if (nextTask.isDefined) {

            idleRunner.get.startTask(nextTask.get)
            setTaskToNextIdleRunner
          } 
        }        
      } 
      
      setTaskToNextIdleRunner

      val allIdle = ! _runnerHandles.exists( ! _.isIdle )

      if (allIdle) {
        println("all runners idle, terminating runners")
        _runnerHandles.foreach(_.terminate)
        postRunLoopExit()
      } 
    } 
  } 

  @tailrec private def tryPortRange(range:List[Int]):Service = {
    if (range.isEmpty) null
    else {
      val address = new AddressByName(addressString, range.head)
      try {
        return new Service(runLoop,
                           address,
                           onNewConnection _,
                           new SocketConnectionHandle.Config(SocketConnectionHandle.DefaultConfig.pingInterval,
                                                             pingTimeout = ProbingConfig.ProbeConnectionTimeout))
      } catch {
        case _:Service.AddressUsedException => { /* ignore */ }
      }

      return tryPortRange(range.tail)
    }
  }

  private val service = tryPortRange(portRange)

  
  if (service != null) {
    println("Probe controlling service bound at " + serviceBindAddress)
    val writer = IO.fileWriter("port")
    writer.write(serviceBindAddress.port.toString)
    writer.newLine
    writer.close
  } else {
    throw new ProbeController.CantStartService
  }


  def serviceBindAddress = service.bindAddress
  
  def run() {
    runLoop()
    service.close()
  }

  private def postRunLoopExit() = {
    runLoop.post(() => {
      runLoop.terminate()
    } )
  } 
}
