package testing.service
import org.scalatest.FlatSpec
import java.io.IOException
import org.scalamock.scalatest.MockFactory
import tas.concurrency.RunLoop

import tas.types.Interval

import tas.service.{
  ConnectionHandle,
  SocketConnectionHandle,
  Service,
  AddressByName
}

class ServiceTests extends FlatSpec with MockFactory {
  behavior of "Service"



  
  it should "start listening/close and join thread" in {
    val runLoop = new RunLoop

    val newConnectionHandler = mock[(ConnectionHandle)=>Unit]

    val service = new Service(runLoop,
                              new AddressByName("localhost", 9090),
                              newConnectionHandler)

    service.close()
  }


  it should "throw AddressUsedException if address used" in {
    val runLoop = new RunLoop

    val newConnectionHandler = mock[(ConnectionHandle)=>Unit]

    val first = new Service(runLoop,
                            new AddressByName("localhost", 9090),
                            newConnectionHandler)

    intercept[Service.AddressUsedException] {
      new Service(runLoop,
                  new AddressByName("localhost", 9090),
                  newConnectionHandler)
    }

    first.close()
  }

  it should "correctly receive pings" in {
    val runLoop = new RunLoop

    runLoop.postDelayed(Interval.seconds(1),
                        () => runLoop.terminate())

    val noPacketExpected = mock[(Array[Byte])=>Unit]
    val noDisconnectExpected = () => throw new Error("Should not disconnect!")

    val fastPingTimeout = SocketConnectionHandle.Config(pingInterval = Interval.milliseconds(100),
                                                        pingTimeout = Interval.milliseconds(300))

    def onNewConnection(client:ConnectionHandle) = {
      client.setHandlers(onPacket = noPacketExpected,
                         onDisconnect = noDisconnectExpected)
    }

    val address = new AddressByName("localhost", 9090)

    val service = new Service(runLoop,
                              address,
                              onNewConnection,
                              connectionHandleConfig = fastPingTimeout)

    val client = SocketConnectionHandle.connect(runLoop,
                                                address,
                                                fastPingTimeout)

    client.setHandlers(onPacket = noPacketExpected,
                       onDisconnect = noDisconnectExpected)

    runLoop()

    service.close()
  }

  it should "detect ping timeout on server from client" in {
    val runLoop = new RunLoop

    runLoop.postDelayed(Interval.seconds(2),
                        () => throw new Error("Test failed, timed out"))

    val noPacketExpected = mock[(Array[Byte])=>Unit]

    val fastPingTimeout = SocketConnectionHandle.Config(pingInterval = Interval.seconds(1),
                                                        pingTimeout = Interval.milliseconds(500))

    def onNewConnection(client:ConnectionHandle) = {
      client.setHandlers(onPacket = noPacketExpected,
                         onDisconnect = () => {
                             runLoop.terminate()
                           })
    }

    val address = new AddressByName("localhost", 9090)

    val service = new Service(runLoop,
                              address,
                              onNewConnection,
                              connectionHandleConfig = fastPingTimeout)

    val client = SocketConnectionHandle.connect(runLoop,
                                                address)

    runLoop()

    service.close()
  }

  it should "detect ping timeout on client from server" in {
    val runLoop = new RunLoop

    runLoop.postDelayed(Interval.seconds(2),
                        () => throw new Error("Test failed, timed out"))

    val noPacketExpected = mock[(Array[Byte])=>Unit]

    val fastPingTimeout = SocketConnectionHandle.Config(pingInterval = Interval.seconds(1),
                                                        pingTimeout = Interval.milliseconds(500))

    def onNewConnection(client:ConnectionHandle) = {
      client.setHandlers(onPacket = noPacketExpected,
                         onDisconnect = () => throw new Error("RunLoop terminated, this should not be called"))
    }

    val address = new AddressByName("localhost", 9090)

    val service = new Service(runLoop,
                              address,
                              onNewConnection)

    val client = SocketConnectionHandle.connect(runLoop,
                                                address,
                                                config = fastPingTimeout)

    client.setHandlers(onPacket = noPacketExpected,
                       onDisconnect = () => {
                           runLoop.terminate()
                         } )

    runLoop()

    service.close()
  }



} 
