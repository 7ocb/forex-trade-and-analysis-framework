package tests.hub

import tas.output.logger.{
  ScreenLogger,
  NullLogger
}

import org.scalatest.FlatSpec

import org.scalamock.scalatest.MockFactory

import tas.types.{
  Interval,
  Fraction,
  Price
}

import tas.concurrency.RunLoop

import tas.hub.{
  HubService,
  HubProtocol
}

import tas.service.{
  AddressByName,
  ConnectionHandle,
  SocketConnectionHandle
}

class HubServiceTests extends FlatSpec with MockFactory {
  val ServiceAddress = new AddressByName("127.0.0.1", 9000)
  val TestTimeout = Interval.seconds(4)

  // val logger = ScreenLogger
   val logger = NullLogger

  behavior of "hub service"

  def runTest(testBody:(RunLoop, HubService)=>Unit) = {
    val runLoop = new RunLoop()
    runLoop.postDelayed(TestTimeout,
                        () => throw new Error("Test timed out"))


    val service = new HubService(runLoop,
                                 logger,
                                 ServiceAddress)

    testBody(runLoop,
            service)

    runLoop()

    service.close()
  }

  it should "send nothing found if nothing found and close connection" in runTest(
    (runLoop, service) => {
      val shouldBeCalled = mock[()=>Unit]
      (shouldBeCalled.apply _).expects()

      val connectionToService = SocketConnectionHandle.connect(runLoop, ServiceAddress)

      connectionToService.sendRawData(HubProtocol.writePacket(new HubProtocol.AskTicksProvider("eurusd")))

      connectionToService.setHandlers(onPacket = (packet) => {
                                          assert(HubProtocol.readPacket(packet) === new HubProtocol.ResourceNotFound())
                                          shouldBeCalled()
                                        },
                                      onDisconnect = () => {
                                          connectionToService.close()
                                          runLoop.terminate()
                                        } )

    })

  it should "register ticks source and ticks listener, propagate ticks and close client" in runTest (
    (runLoop, service) => {
      val onRegisteredCall = mock[()=>Unit]
      val onResouceConnectedCall = mock[()=>Unit]
      val onTickCall = mock[(Price)=>Unit]
      val clientDisconnectCall = mock[()=>Unit]

      inSequence {
        (onRegisteredCall.apply _).expects()
        (onResouceConnectedCall.apply _).expects()
        (onTickCall.apply _).expects(Price.fromBid(Fraction("1.1"), 1))
        (onTickCall.apply _).expects(Price.fromBid(Fraction("2.1"), 2))
        (onTickCall.apply _).expects(Price.fromBid(Fraction("0.001"), 3))
        (clientDisconnectCall.apply _).expects()
      }

      val providerConnection = SocketConnectionHandle.connect(runLoop, ServiceAddress)

      providerConnection.sendRawData(HubProtocol.writePacket(new HubProtocol.RegisterTicksProvider("eurusd")))

      def onRegistered() = {

        val listenerConnection = SocketConnectionHandle.connect(runLoop, ServiceAddress)

        listenerConnection.sendRawData(HubProtocol.writePacket(new HubProtocol.AskTicksProvider("eurusd")))

        def onResouceConnected() = {
          // send several ticks

          def sendTick(price:Price) = providerConnection.sendRawData(HubProtocol.writePacket(new HubProtocol.OnTick(price.bid, price.ask)))

          sendTick(Price.fromBid(Fraction("1.1"), 1))
          sendTick(Price.fromBid(Fraction("2.1"), 2))
          sendTick(Price.fromBid(Fraction("0.001"), 3))
          sendTick(Price.fromBid(Fraction("0"), 0))
        }

        (onTickCall.apply _).expects(Price.ZERO).onCall((ignoredPrice:Price) => {
                                                             providerConnection.close()
                                                           } )

        listenerConnection.setHandlers(onPacket = (packet) => {
                                           HubProtocol.readPacket(packet) match {
                                             case _:HubProtocol.TickProviderConnected => {
                                               onResouceConnectedCall()
                                               onResouceConnected()
                                             }

                                             case HubProtocol.OnTick(bid, ask) => {
                                               onTickCall(new Price(bid, ask))
                                             }

                                             case _ => throw new Error("hub sent wrong packet")
                                           }
                                         } ,
                                       onDisconnect = () => {
                                           clientDisconnectCall()
                                           runLoop.terminate()
                                         } )
      }

      providerConnection.setHandlers(onPacket = (packet) => {
                                         HubProtocol.readPacket(packet) match {
                                           case _:HubProtocol.Registered => {
                                             onRegistered()
                                             onRegisteredCall()
                                           }
                                           case _ => throw new Error("hub sent wrong packet")
                                         }
                                       },
                                     onDisconnect = () => {
                                         throw new Error("should not be called")
                                       } )

    })

  it should "register ticks source and send 'already registered' to second with same key" in runTest (
    (runLoop, service) => {
      val onRegisteredCall = mock[()=>Unit]
      val onAlreadyRegisteredCall = mock[()=>Unit]

      inSequence {
        (onRegisteredCall.apply _).expects()

        (onAlreadyRegisteredCall.apply _).expects()
      }

      val providerConnection = SocketConnectionHandle.connect(runLoop, ServiceAddress)

      providerConnection.sendRawData(HubProtocol.writePacket(new HubProtocol.RegisterTicksProvider("eurusd")))

      def onRegistered() = {
        val secondProviderConnection = SocketConnectionHandle.connect(runLoop, ServiceAddress)

        secondProviderConnection.sendRawData(HubProtocol.writePacket(new HubProtocol.RegisterTicksProvider("eurusd")))

        secondProviderConnection
          .setHandlers(onPacket = (packet) => {
                           HubProtocol.readPacket(packet) match {
                             case _:HubProtocol.AlreadyRegistered => {
                               onAlreadyRegisteredCall()
                             }
                             case _ => throw new Error("hub sent wrong packet")
                           }
                         },
                       onDisconnect = () => {

                           providerConnection.close()
                           secondProviderConnection.close()
                           runLoop.terminate()
                         } )
      }

      providerConnection.setHandlers(onPacket = (packet) => {
                                         HubProtocol.readPacket(packet) match {
                                           case _:HubProtocol.Registered => {
                                             onRegistered()
                                             onRegisteredCall()
                                           }
                                           case _ => throw new Error("hub sent wrong packet")
                                         }
                                       },
                                     onDisconnect = () => {
                                         throw new Error("should not be called")
                                       } )

    })


}
