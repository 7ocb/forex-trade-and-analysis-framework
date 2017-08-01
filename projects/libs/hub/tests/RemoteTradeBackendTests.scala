package testing.service

import org.scalatest.FlatSpec
import org.scalamock.scalatest.MockFactory

import tas.events.SyncCallSubscription

import tas.service.{
  Service,
  Address,
  AddressByName,
  ConnectionHandle,
  SocketConnectionHandle
}

import scala.collection.mutable.ListBuffer

import tas.trading.{
  TradeRequest,
  TradeBackend,
  TradeExecutor,
  AccountInformationKeeper
}

import tas.concurrency.RunLoop

import tas.types.{
  Interval,
  Sell,
  Boundary
}
import tas.NotBound

import tas.output.logger.{
  Logger,
  ScreenLogger,
  NullLogger
}

import tas.hub.clients.RemoteTradeBackend

import tas.hub.clients.connected.ConnectedRemoteTradeBackend

import tas.hub.BindClientConnectionToRemoteBackend

import testing.utils.RunLoopSupport

import tas.hub.HubProtocol

class RemoteTradingRelayService(logger:Logger,
                                runLoop:RunLoop,
                                addr:Address,
                                slaveBackend:RemoteTradeBackend) {

  private val service = new Service(runLoop,
                                    addr,
                                    onNewConnection _)

  private val _bindings = new ListBuffer[BindClientConnectionToRemoteBackend]

  private def onNewConnection(connection:ConnectionHandle) = {
    _bindings += new BindClientConnectionToRemoteBackend(logger,
                                                         connection,
                                                         slaveBackend,
                                                         _bindings -= _)
  }

  def close() = {
    service.close()
    _bindings.foreach(_.close())
  }

}

object Constants {
  // this id have no meaning, actually, just randomly selected value
  val TestBoundary = Boundary >= 5

  val TestRequest = new TradeRequest(10,
                                     Sell,
                                     Some(TestBoundary))

}

object RemoteTradeBackendTests {
  val address = new AddressByName("127.0.0.1", 7592)
}

class RemoteTradeBackendTests extends FlatSpec with MockFactory with RunLoopSupport {

  def ignoreMessages(message:String) = {}

  val logger = NullLogger
  // val logger = ScreenLogger

  behavior of "trading service connection layer"

  trait Executor extends TradeExecutor with NotBound

  
  it should "propagate basic lifecycle events and gracefully terminate" in runLoopTest {

    var service:RemoteTradingRelayService = null
    var client:ConnectedRemoteTradeBackend = null

    def succeedTest = {
      info("succeed test")
      client.close()
      service.close()
      complete
    }
    
    val slaveBackend = mock[RemoteTradeBackend]
    val mockExecutor = mock[Executor]
    val notCalled = mock[()=>Unit]

    val onTradeFreed = mock[()=>Unit]

    inSequence {
      { (slaveBackend.balanceMayBeChanged _).expects().returning(new SyncCallSubscription) }
      { (slaveBackend.equityMayBeChanged _).expects().returning(new SyncCallSubscription) }
      { (slaveBackend.newTradeExecutor _).expects(Constants.TestRequest,
                                                  *,
                                                  *).returning(mockExecutor) }
      { (mockExecutor.openTrade _).expects(Constants.TestBoundary,
                                           None,
                                           *,
                                           *) }
      { (mockExecutor.setStop _).expects(Constants.TestBoundary)
                                        (mockExecutor.setTakeProfit _).expects(Some(Constants.TestBoundary)) }
      { (mockExecutor.closeTrade _).expects().onCall(succeedTest _) }
    }
    

    
    service = new RemoteTradingRelayService(logger,
                                            runLoop,
                                            RemoteTradeBackendTests.address,
                                            slaveBackend)

    
    info("service started")
    client = new ConnectedRemoteTradeBackend(SocketConnectionHandle.connect(runLoop,
                                                                            RemoteTradeBackendTests.address),
                                             logger,
                                             "0",
                                             "0",
                                             () => {})


    val executor = client.newTradeExecutor(Constants.TestRequest,
                                           ignoreMessages,
                                           onTradeFreed)
    executor.openTrade(Constants.TestBoundary,
                       None,
                       notCalled,
                       notCalled)
    executor.setStop(Constants.TestBoundary)
    executor.setTakeProfit(Some(Constants.TestBoundary))
    executor.closeTrade()
  }


  it should "close client's trades on client disconnected" in runLoopTest {

    val slaveBackend = mock[RemoteTradeBackend]

    val notCalled = mock[()=>Unit]

    var service:RemoteTradingRelayService = null
    var client:ConnectedRemoteTradeBackend = null

    val onTradeFreed = mock[()=>Unit]

    def succeedTest():Unit = {
      info("closing service")
      service.close()

      complete
    }
    
    inSequence {

      { (slaveBackend.balanceMayBeChanged _).expects().returning(new SyncCallSubscription) }
      { (slaveBackend.equityMayBeChanged _).expects().returning(new SyncCallSubscription) }


      def setupExpectSequence(body: =>Unit) = {
        val mockExecutor = mock[Executor]

        (slaveBackend.newTradeExecutor _).expects(Constants.TestRequest,
                                                  *,
                                                  *).returning(mockExecutor)

        (mockExecutor.openTrade _).expects(Constants.TestBoundary,
                                           None,
                                           *,
                                           *).onCall((_,_,_,_) => body)
        mockExecutor
      }

      val first = setupExpectSequence {}
      val second = setupExpectSequence {}
      val third = setupExpectSequence {
          info("closing client")
          client.close()
        }

      // order of calls inverted because RemoteTradingRelayService client connection
      // keeps executors in map and it seems that map keeps keys not in
      // sorted order.

      
      { (second.closeTrade _).expects() }
      { (first.closeTrade _).expects() }
      { (third.closeTrade _).expects().onCall(succeedTest _) }

      info("set succeed test on close call")
      
    }
    
    service = new RemoteTradingRelayService(logger,
                                            runLoop,
                                            RemoteTradeBackendTests.address,
                                            slaveBackend)

    info("service started")
    
    client = new ConnectedRemoteTradeBackend(SocketConnectionHandle
                                               .connect(runLoop,
                                                        RemoteTradeBackendTests.address),
                                    logger,
                                    "0",
                                    "0",
                                    () => {})


    client.newTradeExecutor(Constants.TestRequest,
                            ignoreMessages,
                            onTradeFreed).openTrade(Constants.TestBoundary,
                                                      None,
                                                      notCalled,
                                                      notCalled)

    client.newTradeExecutor(Constants.TestRequest,
                            ignoreMessages,
                            onTradeFreed).openTrade(Constants.TestBoundary,
                                                      None,
                                                      notCalled,
                                                      notCalled)

    client.newTradeExecutor(Constants.TestRequest,
                            ignoreMessages,
                            onTradeFreed).openTrade(Constants.TestBoundary,
                                                      None,
                                                      notCalled,
                                                      notCalled)
    info("spawned trades")

  }

  it should "propagate message for trade" in runLoopTest {

    import HubProtocol._

    val connection = mock[ConnectionHandle]
    val onDisconnect = mock[()=>Unit]

    val message = "test message"
    val tradeId = 1

    var rawPacketHandler:(Array[Byte])=>Unit = null

    (connection.setHandlers _).expects(*, *).onCall((packetHandler, _) => {
                                                      rawPacketHandler = packetHandler
                                                    })

    val backend = new ConnectedRemoteTradeBackend(connection,
                                                  logger,
                                                  "0",
                                                  "0",
                                                  onDisconnect)

    def onIdRequested(packet:Array[Byte]) = {
      assert(readPacket(packet) === new RequestNewId())
      runLoop.post(() => {
                     rawPacketHandler(writePacket(new NewId(tradeId)))
                   } )

      runLoop.post(() => {
                     rawPacketHandler(writePacket(new MessageAboutTrade(tradeId,
                                                                        message)))
                   } )
    }

    (connection.sendRawData _).expects(*).onCall(onIdRequested _)

    val messageExpector = mock[String=>Unit]

    (messageExpector.apply _).expects(message).onCall((string:String) => complete)

    val onTradeFreed = mock[()=>Unit]

    val executor = backend.newTradeExecutor(Constants.TestRequest,
                                            messageExpector,
                                            onTradeFreed)

  }


  it should ("still propagate messages after trade executor closed\n"
               + "  and stop only after FreeTrade") in runLoopTest {

    info("The one warning about message for executor without id is correct here")

    import HubProtocol._

    val connection = mock[ConnectionHandle]
    val onDisconnect = mock[()=>Unit]

    val messages = List("test message",
                        "second message")
    val tradeId = 1

    var rawPacketHandler:(Array[Byte])=>Unit = null

    (connection.setHandlers _).expects(*, *).onCall((packetHandler, _) => {
                                                      rawPacketHandler = packetHandler
                                                    })

    val backend = new ConnectedRemoteTradeBackend(connection,
                                                  logger,
                                                  "0",
                                                  "0",
                                                  onDisconnect)

    def sendMessage(message:String) = {
      rawPacketHandler(writePacket(new MessageAboutTrade(tradeId,
                                                         message)))
    }

    def onIdRequested(packet:Array[Byte]) = {
      assert(readPacket(packet) === new RequestNewId())
      runLoop.post(() => {
                     rawPacketHandler(writePacket(new NewId(tradeId)))
                   } )

      runLoop.post(() => {
                     sendMessage(messages(0))
                   } )
    }

    (connection.sendRawData _).expects(*).onCall(onIdRequested _)

    val messageExpector = mock[String=>Unit]

    val onTradeFreed = mock[()=>Unit]

    (onTradeFreed.apply _).expects()


    val executor = backend.newTradeExecutor(Constants.TestRequest,
                                            messageExpector,
                                            onTradeFreed)

    val onOpened = mock[()=>Unit]
    val onExternallyClosed = mock[()=>Unit]


    (connection.sendRawData _).expects(*)
      .onCall((packet:Array[Byte]) => {
                assert(readPacket(packet) === OpenTrade(tradeId,
                                                        Constants.TestRequest,
                                                        Constants.TestBoundary,
                                                        None))
                runLoop.post(() => {
                               executor.closeTrade()
                               sendMessage(messages(1))
                             })
              } )



    (messageExpector.apply _).expects(messages(0))
      .onCall((string:String) => {
                executor.openTrade(Constants.TestBoundary, None, onOpened, onExternallyClosed)
              })

    def postFreeTrade() = {
      runLoop.post(() => {
                     rawPacketHandler(writePacket(new FreeTrade(tradeId)))
                     sendMessage("should not be received")
                     complete
                   } )
    }

    (connection.sendRawData _).expects(*)
      .onCall((packet:Array[Byte]) => {
                assert(readPacket(packet) === CloseRequest(tradeId))

              } )

    (messageExpector.apply _).expects(messages(1))
      .onCall((string:String) => {
                postFreeTrade()
              })


  }

  
}
