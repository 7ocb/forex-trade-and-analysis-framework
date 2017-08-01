package tests.hub

import tas.types.{
  Fraction,
  Sell,
  Buy,
  Boundary
}

import tas.hub.HubProtocol
import tas.hub.HubProtocol._

import tas.trading.{
  TradeRequest
}

import tests.utils.ProtocolTests

object Constants {
  // this id have no meaning, actually, just randomly selected value
  val TestId = 2
  val TestBoundary = Boundary >= 2
  val TestSellRequest = new TradeRequest(10,
                                         Sell,
                                         Some(TestBoundary))

  val TestBuyRequest = new TradeRequest(25,
                                        Buy,
                                        Some(TestBoundary))
}

class HubProtocolTests extends ProtocolTests[Packet] {

  behavior of "packet writing and reading"

  val testPacket = createTester(HubProtocol.writePacket _,
                                HubProtocol.readPacket _)

  testPacket(List(new RegisterTicksProvider("eurusd"),
                  new RegisterTicksProvider("audusd")))

  testPacket(List(new RegisterTradeConnector("eurusd", "100", "20"),
                  new RegisterTradeConnector("audusd","0.100", "20.1")))

  testPacket(List(new Registered()))

  testPacket(List(new AlreadyRegistered()))  

  testPacket(List(new OnTick(Fraction("0.12"), Fraction("0.13")),
                  new OnTick(Fraction("1.610002"), Fraction("1.6100021"))))

  testPacket(List(new AskTicksProvider("t"),
                  new AskTicksProvider("ee")))

  testPacket(List(new AskTradeConnector("t"),
                  new AskTradeConnector("ee")))
  
  testPacket(List(new TickProviderConnected()))

  testPacket(List(new TradeConnectorConnected("1", "300"),
                  new TradeConnectorConnected("2.3333", "11.0")))

  testPacket(List(new ResourceNotFound()))

  testPacket(List(new RequestNewId()))
  testPacket(List(new NewId(Constants.TestId)))
  testPacket(List(new FreeTrade(Constants.TestId)))

  testPacket(List(new OpenTrade(Constants.TestId,
                                Constants.TestSellRequest,
                                Constants.TestBoundary,
                                None),
                  new OpenTrade(Constants.TestId,
                                Constants.TestBuyRequest,
                                Constants.TestBoundary,
                                Some(Constants.TestBoundary))))

  testPacket(List(new MessageAboutTrade(Constants.TestId,
                                        "message one"),
                  new MessageAboutTrade(3,
                                        "message two")))

  testPacket(List(new CloseRequest(Constants.TestId)))
  testPacket(List(new UpdateStopRequest(Constants.TestId,
                                        Constants.TestBoundary)))

  testPacket(List(new UpdateTakeProfitRequest(Constants.TestId,
                                              Some(Constants.TestBoundary)),

               new UpdateTakeProfitRequest(Constants.TestId,
                                           None)))

  testPacket(List(new OpenedResponse(Constants.TestId)))
  testPacket(List(new ExternallyClosed(Constants.TestId)))

  testPacket(List(new CurrentEquity("100"),
                  new CurrentEquity("200.2")))

  testPacket(List(new CurrentBalance("100"),
                  new CurrentBalance("200.2")))
}
