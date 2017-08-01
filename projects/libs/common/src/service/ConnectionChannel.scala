package tas.service

import java.net.Socket
import tas.concurrency.{
  RunLoop,
  NewThreadWorker
}
import scala.annotation.tailrec

import java.io.{
  InputStream,
  DataOutputStream,
  DataInputStream,
  IOException,
  Closeable
}

import tas.utils.IO

object ConnectionChannel {
  private def receivingLoop(inputStream:InputStream,
                            onPacketRead:Array[Byte]=>Unit,
                            onError:()=>Unit) = {

    val dataStream = new DataInputStream(inputStream)

    @tailrec def readNextPacket:Unit = {
      val size = dataStream.readInt
      val buffer = new Array[Byte](size)

      IO.readAllBuffer(dataStream,
                       buffer)

      onPacketRead(buffer)

      readNextPacket
    }

    try {
      readNextPacket
    } catch {
      case e:Throwable => {
        onError()
      }
    }
  }

  def silently(action: => Unit) = {
    try {
      action
    } catch {
      case _:Throwable => { /* silently ignored */ }
    }
  }
}

final private class ConnectionChannel(runLoop:RunLoop,
                                      socket:Socket,
                                      handlePacket:Array[Byte]=>Unit,
                                      onConnectionLost:()=>Unit) {

  private val _inputStream = socket.getInputStream()
  private val _outputStream = socket.getOutputStream()

  private val _ctThreadId = Thread.currentThread().getId()
  private var _currentState:State = JustCreated

  private val _sendLoop = new RunLoop

  private def forCurrentState(action:State=>Unit) = synchronized {
      action(_currentState)
    }

  private sealed trait State {
    def initFrom(previous:State)
    def sendRawData(data:Array[Byte])

    def onCtIfStateNotChanged(action: =>Unit) = {
      runLoop.post(() => {
                     forCurrentState(state => {
                                       if (state == this) {
                                         action
                                       }
                                     })
                   })
    }

    def deliverReceivedBuffer(data:Array[Byte])

    def onIoError()

    def close() = switchStateTo(Closing)
  }

  private object JustCreated extends State {
    def initFrom(previous:State) = throw new Error("Should never be called!")
    def sendRawData(data:Array[Byte]) = throw new Error("Should never be called!")
    def onIoError() = throw new Error("Should never be called!")
    def deliverReceivedBuffer(data:Array[Byte]) = throw new Error("Should never be called!")
  }

  private trait DeliveringReceivedData {
    def deliverReceivedBuffer(data:Array[Byte]) = handlePacket(data)
  }

  private trait DroppingReceivedData {
    def deliverReceivedBuffer(data:Array[Byte]) = {}
  }

  private object Connected extends State with DeliveringReceivedData {
    def initFrom(previous:State) = {
      previous match {
        case JustCreated => {}
        case _ => throw new Error("Came to Connected from wrong state: " + previous)
      }

      val worker = new NewThreadWorker

      def onThreadReceivedPacket(packet:Array[Byte]) = {
        runLoop.post(() => {
                       forCurrentState(_.deliverReceivedBuffer(packet))
                     })
      }

      def onReceiveThreadError() = {
        forCurrentState(_.onIoError())
      }

      // run receiving thread
      worker.run(() => ConnectionChannel.receivingLoop(socket.getInputStream(),
                                                       onThreadReceivedPacket,
                                                       onReceiveThreadError))

      // run sending thread
      worker.run(() => _sendLoop())
    }


    def sendRawData(data:Array[Byte]) = {
      _sendLoop.post(sendAction(data))
    }

    def onIoError() = switchStateTo(SwitchingToError)
  }

  private object Error extends State with DeliveringReceivedData {
    def initFrom(previous:State) = {
      previous match {
        case SwitchingToError => {}
        case _ => throw new Error("Came to SwitchingToError from wrong state: " + previous)
      }

      // call on connection lost handler
      onConnectionLost()
    }

    def sendRawData(data:Array[Byte]) = { /* can't handle data in error state */ }

    def onIoError() = { /* already in error state */ }
  }

  private object SwitchingToError extends State with DeliveringReceivedData {
    def initFrom(previous:State) = {
      previous match {
        case Connected => {}
        case _ => throw new Error("Came to SwitchingToError from wrong state: " + previous)
      }

      onCtIfStateNotChanged {
        switchStateTo(Error)
      }
    }

    def sendRawData(data:Array[Byte]) = { /* we are going to error state, so data now just lost */ }
    def onIoError() = { /* already switching to error state */ }
  }

  private object Closing extends State with DroppingReceivedData {
    def initFrom(previous:State) = {
      import ConnectionChannel.silently

      // make sure all posted data are send out, so post at the end of sending queue
      _sendLoop.post(() => {

                       silently { _outputStream.flush()  }

                       silently { socket.shutdownOutput() }
                       silently { socket.close() }
                       
                       _sendLoop.terminate()

                       switchStateTo(Closed)
                     } )
    }

    def sendRawData(data:Array[Byte]) = throw new Error("Can't send data in closing state")
    def onIoError() = { /* all errors in closed state are ignored */ }

    override def close() = { /* already closing */ }
    
  }

  private object Closed extends State with DroppingReceivedData {
    def initFrom(previous:State) = {
      previous match {
        case Closing => { /* correct */ }
        case _ => throw new Error("Came to closed from wrong state: " + previous)
      }
    }

    def sendRawData(data:Array[Byte]) = throw new Error("Can't send data in closed state")
    def onIoError() = { /* all errors in closed state are ignored */ }

    override def close() = { /* already closed */ }
    
  }




  private def switchStateTo(state:State) = synchronized {
      val oldState = _currentState
      _currentState = state
      state.initFrom(oldState)
  }

  switchStateTo(Connected)


  def sendRawData(buffer:Array[Byte]) = forCurrentState(_.sendRawData(buffer))

  private def sendAction(buffer:Array[Byte]) = {
    () => {
      try {
        val dataStream = new DataOutputStream(_outputStream)
        dataStream.writeInt(buffer.length)

        if (buffer.length > 0) {
          dataStream.write(buffer)
        }

        dataStream.flush
        
      } catch {
        case e:Throwable => {
          forCurrentState(_.onIoError())
        }
      }
    }
  }

  def close() = forCurrentState(_.close())

}
