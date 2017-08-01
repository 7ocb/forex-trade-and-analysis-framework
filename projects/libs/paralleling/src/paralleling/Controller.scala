package tas.paralleling

import java.io.Serializable

import scala.annotation.tailrec

import scala.collection.mutable.ListBuffer


import tas.concurrency.RunLoop

import scala.Function.tupled

import tas.types.{
  Interval
}

import tas.events.{
  Subscription,
  SyncCallSubscription
}

import tas.service.{
  ConnectionHandle,
  SocketConnectionHandle,
  Service,
  Address,
  AddressByName
}



object Controller {
  private val addressString = "0.0.0.0"
  private val portRange = (9000 to 9100).toList

  private def spawnService(runLoop:RunLoop,
                           onNewConnection:ConnectionHandle=>Unit) = {
    @tailrec def tryPortRange(range:List[Int]):Service = {
      if (range.isEmpty) null
      else {
        val address = new AddressByName(addressString,
                                        range.head)
        try {
          return new Service(runLoop,
                             address,
                             onNewConnection,
                             new SocketConnectionHandle.Config(SocketConnectionHandle.DefaultConfig.pingInterval,
                                                               pingTimeout = Paralleling.ConnectionTimeout))
        } catch {
          case _:Service.AddressUsedException => { /* ignore */ }
        }

        return tryPortRange(range.tail)
      }
    }

    tryPortRange(portRange)
  }

}

final class Controller(runLoop:RunLoop) {

  private val _tasks = new ListBuffer[ActionHandle[_ <: Serializable]]
  private val _runners = new ListBuffer[RunnerHandle]

  private val _onHaveFreeResources = new SyncCallSubscription
  private val _onBecomeIdle = new SyncCallSubscription

  private val _service = Controller.spawnService(runLoop,
                                                 onNewConnection)

  def submit[Result <: Serializable](action:Action[Result],
                                     onResult:Result=>Unit) = {

    _tasks += new ActionHandle(action, onResult)

    dispatchTasks()
  }

  def onHaveFreeResources:Subscription[()=>Unit] = _onHaveFreeResources

  def haveFreeResources:Boolean = _runners.exists(_.isIdle)

  def onBecomeIdle:Subscription[()=>Unit] = _onBecomeIdle

  def shutdown():Unit = {
    _runners.foreach(_.terminate)

    _service.close()
  }

  private def onRunnerLost(runner:RunnerHandle) = {
    _runners -= runner

    runner.task.foreach {
      taskOfLostRunner =>

      _tasks.+=:(taskOfLostRunner)

      dispatchTasks()
    }
  }

  private def onNewConnection(connection:ConnectionHandle) = {
    _runners += new RunnerHandle(connection,
                                 dispatchTasks,
                                 onRunnerLost)
    dispatchTasks()
  }

  private def dispatchTasks() {
    val idleRunners = _runners.filter(_.isIdle)

    if (idleRunners.isEmpty) return

    val dispatchedTasks =
      idleRunners
        .zip(_tasks)
        .map { case (runner, task) =>
          runner.startTask(task)
          task
      }

    _tasks --= dispatchedTasks

    if (haveFreeResources) _onHaveFreeResources()

    if (!_runners.map(_.isIdle).contains(false)) _onBecomeIdle()
  }
}


