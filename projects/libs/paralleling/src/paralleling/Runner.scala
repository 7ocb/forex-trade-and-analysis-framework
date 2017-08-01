package tas.paralleling

import java.io.Serializable
import tas.service.{
  ConnectionHandle,
  SocketConnectionHandle
}
import tas.service.AddressByAddress
import tas.concurrency.{
  RunLoop,
  ThreadPoolWorker
}
import tas.service.AddressByName
import java.io.File

import tas.output.logger.Logger
import tas.output.logger.FileLogger

object Runner extends App {

  private val worker = new ThreadPoolWorker()

  private var connection:ConnectionHandle = null
  val runLoop = new RunLoop

  if (args.length > 0) {
    if (args.length > 1) println("extra arguments ignored")


    val addressString = args(0)
    val addressParts = addressString.split(":")

    if (addressParts.length != 2) {
      malformedAddress(addressString)
    } else {
      println("started, binding to " + addressString)

      try {
        val port = addressParts(1).toInt

        connection = SocketConnectionHandle.connect(runLoop,
                                                    new AddressByName(addressParts(0),
                                                                      port),
                                                    new SocketConnectionHandle.Config(SocketConnectionHandle.DefaultConfig.pingInterval,
                                                                                      pingTimeout = Paralleling.ConnectionTimeout))

        def onConnectionLost() = {
          println("lost connection to server")
          runLoop.terminate()
        }

        connection.setHandlers(onPacket = onServiceCommand _,
                               onDisconnect = onConnectionLost _)

        runLoop()
      } catch {
        case _:NumberFormatException => malformedAddress(addressString)
      }

    }

  } else {
    println("target address not specified, args.lenght == 0")
  }


  private def onServiceCommand(packet:Array[Byte]):Unit = {
    Protocol.readPacket(packet) match {
      case packet:Protocol.ServerToClientPacket => packet match {
        case Protocol.RunTask(task) => onNewTask(task)
        case Protocol.Terminate() => {
          println("received terminate")
          terminate()
        }
      }

      case _:Protocol.ClientToServerPacket => throw new RuntimeException("server sending client->server packet!")
    }
  }

  private def onNewTask(task:Action[_ <: Serializable]) = {
    worker.run(() =>
      {
        try {
          val result = task.run()

          runLoop.post(() => {
                         connection.sendRawData(Protocol.writePacket(new Protocol.TaskCompleted(result)))
                       } )

        } catch {
          case e:Throwable => {
            println("exception: " + e)
            e.getStackTrace().foreach(element => println("   " + element.toString()))
            println("exception in runner is fatal, terminating")
            terminate()
          }
        }
      })
  }

  private def terminate() {
    connection.close()
    runLoop.terminate()
  }

  private def malformedAddress(addr:String) = {
    println("malformed address: " + addr)
  }


}
