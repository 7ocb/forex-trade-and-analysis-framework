package tas.paralleling

import java.io.Serializable

import tas.service.{
  ConnectionHandle
}

private [paralleling] final class RunnerHandle(connection:ConnectionHandle,
                                 onIdle:()=>Unit,
                                 onLost:(RunnerHandle)=>Unit) {

  private var _terminated = false
  private var _currentTask:Option[ActionHandle[_ <: Serializable]] = None

  def onIncomingPacket(array:Array[Byte]) = {
    Protocol.readPacket(array) match {
      case packet:Protocol.ClientToServerPacket => packet match {
        case Protocol.TaskCompleted(result) => onRunnerCompletedTask(result)
      }
      case _:Protocol.ServerToClientPacket => throw new RuntimeException("client sending server->client packet!")
    }
  }

  connection.setHandlers(onPacket = onIncomingPacket _,
                         onDisconnect = onRunnerLost _)

  private def onRunnerCompletedTask(result:Serializable) = {
    if (_currentTask == None) throw new IllegalStateException("runner is idle, can't receive result")

    _currentTask.foreach(_.processResult(result))

    _currentTask = None

    onIdle()
  }

  private def onRunnerLost() = {
    if (! _terminated) {
      _terminated = true
      connection.close()
      onLost(this)
    }
  }

  def task = _currentTask

  def isIdle = _currentTask == None

  def startTask(task:ActionHandle[_ <: Serializable]):Unit = {
    if (_currentTask != None) throw new IllegalStateException("runner is busy")
    if (_terminated) throw new IllegalStateException("runner is terminated")

    _currentTask = Some(task)

    connection.sendRawData(Protocol.writePacket(new Protocol.RunTask(_currentTask.get.actionToSend)))
  }

  def terminate = {
    _terminated = true
    connection.sendRawData(Protocol.writePacket(new Protocol.Terminate()))
    connection.close()
  }
}
