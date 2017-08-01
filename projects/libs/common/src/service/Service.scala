package tas.service

import scala.annotation.tailrec
import tas.concurrency.RunLoop
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import tas.concurrency.NewThreadWorker


import java.io.IOException

abstract class Address(val port:Int) {
  def inetAddress:InetAddress

  override def toString:String = inetAddress.getHostAddress + ":" + port
}

class AddressByName(val hostName:String, port:Int) extends Address(port) {
  def inetAddress = InetAddress.getByName(hostName)
}

class AddressByAddress(val inetAddress:InetAddress, port:Int) extends Address(port)


object Service {
  class AddressUsedException() extends Exception("Address in use")
}   

class Service(runLoop:RunLoop,
              val bindAddress:Address,
              newConnectionHandler:(ConnectionHandle)=>Unit,
              connectionHandleConfig:SocketConnectionHandle.Config = SocketConnectionHandle.DefaultConfig)
{

  var _closed = false
  
  val listenSocket = new ServerSocket()
  listenSocket.setReuseAddress(true)

  val inetSockedAddress = new InetSocketAddress(bindAddress.inetAddress,
                                                bindAddress.port)
  try {
    listenSocket.bind(inetSockedAddress)
  } catch {
    case e:java.net.BindException => {
      listenSocket.close()
      throw new Service.AddressUsedException()
    } 
  }  

  val listenThread = new Thread() { override def run = listening }
  listenThread.start

  private def listening:Unit = {
    @tailrec
    def acceptNextConnection:Unit = {
      val clientSocket = listenSocket.accept()

      runLoop.post(() => {
        val connection = new SocketConnectionHandle(runLoop, clientSocket, connectionHandleConfig)
        onClientConnected(connection)
      })

      acceptNextConnection
    }

    try {
      acceptNextConnection
    } catch {
      case e:IOException => {
        if ( ! _closed) {
          throw e
        } 
      } 
    }
  }

  def close() = {
    _closed = true
    listenSocket.close
    listenThread.join
  } 

  private def onClientConnected(connection:ConnectionHandle) = {
    newConnectionHandler(connection)
  } 
} 
