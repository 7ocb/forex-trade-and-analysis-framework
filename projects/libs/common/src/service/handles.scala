package tas.service

import tas.types.Interval
import java.net.Socket
import java.net.InetSocketAddress

import tas.concurrency.{
  RunLoop
}

import java.io.ObjectOutputStream
import java.io.ObjectInputStream
import scala.annotation.tailrec
import java.util.Arrays
import java.io.IOException
import java.io.Closeable
import tas.output.warning.Warning


trait ConnectionHandle {
  def sendRawData(buffer:Array[Byte]):Unit

  def setHandlers(onPacket:Array[Byte]=>Unit,
                  onDisconnect:()=>Unit):Unit

  def close()
}

object SocketConnectionHandle {

  case class Config(val pingInterval:Interval, val pingTimeout:Interval)

  val DefaultConfig = Config(pingInterval = Interval.seconds(1),
                             pingTimeout  = Interval.seconds(7))


  def connect(runLoop:RunLoop,
              address:InetSocketAddress,
              config:SocketConnectionHandle.Config):SocketConnectionHandle =
    new SocketConnectionHandle(runLoop,
                               {
                                 val socket = new Socket()
                                 socket.connect(address)
                                 socket
                               },
                               config)

  def connect(runLoop:RunLoop,
              address:Address,
              config:SocketConnectionHandle.Config):SocketConnectionHandle =
    connect(runLoop,
            new InetSocketAddress(address.inetAddress,
                                  address.port),
            config)

  def connect(runLoop:RunLoop,
              address:Address):SocketConnectionHandle =
    connect(runLoop, address,
            SocketConnectionHandle.DefaultConfig)

}

final class SocketConnectionHandle(runLoop:RunLoop,
                                   socket:Socket,
                                   config:SocketConnectionHandle.Config) extends ConnectionHandle {

  private val connection:ConnectionChannel =
    new ConnectionChannel(runLoop,
                          socket,
                          onBufferReceived _,
                          onConnectionLost _)

  private var handlePacket:Array[Byte]=>Unit = null
  private var handleDisconnect:()=>Unit = null


  private val pinger:Pinger = new Pinger(runLoop,
                                         config.pingInterval,
                                         config.pingTimeout) {
      def sendPing() = {
        sendRawData(Pinger.PingPacket)
      }

      def onPingTimedOut() = {
        println("Ping timed out!")
        onConnectionLost()
      }
    }


  override def sendRawData(buffer:Array[Byte]) = connection.sendRawData(buffer)

  override def setHandlers(onPacket:Array[Byte]=>Unit,
                           onDisconnect:()=>Unit):Unit = {
    handlePacket = onPacket
    handleDisconnect = onDisconnect
  }

  override def close() = {
    pinger.stop()
    connection.close()
  }

  private def onBufferReceived(buffer:Array[Byte]) = {
    val isNotPing = ! pinger.consumePing(buffer)

    if (isNotPing) {
      if (handlePacket == null) throw new IllegalStateException("Packet handler should not be null.")

      handlePacket(buffer)
    }
  }
  
  private def onConnectionLost() = {
    if (handleDisconnect == null) throw new IllegalStateException("Disconnect handler should not be null.")

    pinger.stop()
    connection.close()

    handleDisconnect()
  }

}
