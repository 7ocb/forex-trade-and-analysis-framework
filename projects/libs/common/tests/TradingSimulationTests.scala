package testing.trading


import tas.types.Fraction.int2Fraction

import tas.trading.simulation.TradingSimulation
import tas.trading.simulation.SimulationError
import org.scalatest.FlatSpec
import org.scalamock.scalatest.MockFactory
import tas.events.Event

import tas.timers.Timer
import tas.timers.JustNowFakeTimer

import tas.trading.TradeRequest

import tas.types.{
  Price,
  Fraction,
  Trade, Buy, Sell,
  Boundary
}

import tas.output.logger.ScreenLogger
import tas.output.logger.NullLogger

import tas.trading.TradeExecutor

import tas.trading.simulation.config.{
  Config,
  OpenAndCloseBy,
  ComissionFactor
}

import tas.trading.simulation.config.limits.{
  Limits,
  IndependentStops,
  NoLimits
}

class TradingSimulationTests extends FlatSpec with MockFactory {

  def verbose(message:String) = {
    // uncomment this if verbose needed
    // info(message)
    // println("verbose: " + message)
  }
  
  // val logger = ScreenLogger
  val logger = NullLogger
  

  val timer = new JustNowFakeTimer
  
  behavior of "trading simulator"

  
  class Test {

    def limits:Limits = NoLimits

    def openAndCloseBy:OpenAndCloseBy = OpenAndCloseBy.WorstPrice

    def config = new Config(leverage,
                            initialBalance,
                            openAndCloseBy,
                            limits,
                            new ComissionFactor(comissionFactor))

    private var startPrice:Price = new Price(Fraction.ZERO, Fraction.ZERO)
    var comissionFactor:Fraction = Fraction.ZERO
    
    var initialBalance = Fraction(100)

    def leverage = Fraction(100)

    lazy val ticker = Event.newSync[Price]
    
    lazy val trading = {
      val t = new TradingSimulation(config,
                                    timer,
                                    logger,
                                    ticker)
      tick(startPrice)

      t
    }

    
    def tick(bidAndAck:Fraction):Unit = {
      tick(new Price(bidAndAck, bidAndAck))
    }

    def tickBidAsk(bid:Fraction, ask:Fraction):Unit = {
      tick(new Price(bid, ask))
    }

    def tick(price:Price):Unit = {
      logger.log("ticking: ", price)
      startPrice = price
      ticker << price
    }
  }

  def expectCall = {
    val f = mock[() => Unit]
      (f.apply _).expects()
    f
  }

  def failIfCalled = mock[() => Unit]

  it should "execute profiting buy with commission (one time close)" in new Test {
    // ### Profiting buy with comission ###

    // 0. Start conditions:
    verbose("0. Start conditions")
    // Leverage = 100
    // Comission = 0.0003
    comissionFactor = "0.0003"
    // Balance_BC = 100
    initialBalance = Fraction(100)
    // Margin_BC = 0
    assert(trading.margin === Fraction(0))
    // Free Margin_BC = 100
    assert(trading.freeMargin === Fraction(100))
    // Profit = 0
    // Equity_BC = 100
    assert(trading.equity === Fraction(100))
    // Margin_Level_% = N/A
    assert(trading.marginLevel === None)

    // **** Entered on buy trade ****
    // 1. Buy 1000 on price = 1.3158:
    verbose("1. Buy 1000 on price = 1.3158")
    tick("1.3158")

    val executor = trading.newTradeExecutor(new TradeRequest(1000,
                                                             Buy,
                                                             None))

    executor.openTrade(Boundary < 1,
                       None,
                       expectCall,
                       failIfCalled)

    // Balance_BC = 100
    assert(trading.balance === Fraction(100))
    // Margin_BC = 13.158
    assert(trading.margin === Fraction("13.158"))

    // Free Margin_BC = 86.542
    assert(trading.freeMargin === Fraction("86.542"))
    
    // Profit = -0.3
    assert(trading.profit === Fraction("-0.3"))
    
    // Equity_BC = 99.7
    assert(trading.equity === Fraction("99.7"))
    // Margin_Level_% = 757,714%
    assert(trading.marginLevel === Some(Fraction("7.577139382884936920504635962912297")))

    // **** Profiting price for buy ****
    // 2. Price changed to 1.3171
    verbose("2. Price changed to 1.3171")
    tick("1.3171")

    // Balance_BC = 100
    assert(trading.balance === Fraction(100))
    // Margin_BC = 13.158
    assert(trading.margin === Fraction("13.158"))

    // Free Margin_BC = 87.842
    assert(trading.freeMargin === Fraction("87.842"))
    
    // Profit = 1
    assert(trading.profit === Fraction("1"))
    // Equity_BC = 101
    assert(trading.equity === Fraction("101"))
    
    // Margin_Level_% = 767,594%
    assert(trading.marginLevel === Some(Fraction("7.675938592491260069919440644474844")))

    // **** Lossing price for buy ****
    // 3. Price changed to 1.3100
    verbose("3. Price changed to 1.3100")
    tick("1.31")

    // Balance_BC = 100
    assert(trading.balance === Fraction(100))

    // Margin_BC = 13.158
    assert(trading.margin === Fraction("13.158"))

    // Free Margin_BC = 80.742
    assert(trading.freeMargin === Fraction("80.742"))
    
    // Profit = -6.1
    assert(trading.profit === Fraction("-6.1"))

    // Equity_BC = 93.9
    assert(trading.equity === Fraction("93.9"))

    // Margin_Level_% = 713,634%
    assert(trading.marginLevel === Some(Fraction("7.136342909256725946192430460556316")))

    // **** Closed trade ****
    // 4. Trade closed at 1.3181
    verbose("4. Trade closed at 1.3181")
    tick("1.3181")

    executor.closeTrade

    info("check second close - should do nothing")
    executor.closeTrade
    
    // Balance_BC = 102
    assert(trading.balance === Fraction("102"))

    // Margin_BC = 0
    assert(trading.margin === Fraction(0))

    // Free Margin_BC = 102
    assert(trading.freeMargin === Fraction("102"))

    // Profit = 0
    assert(trading.profit === Fraction(0))
    
    // Equity_BC = 102
    assert(trading.equity === Fraction("102"))
    
    // Margin_Level_% = N/A
    assert(trading.marginLevel === None)
  }

  it should "execute delayed profiting buy with commission" in new Test {
    // ### Profiting buy with comission ###

    // 0. Start conditions:
    verbose("0. Start conditions")
    // Comission = 0.0003
    comissionFactor = "0.0003"
    // Balance_BC = 100
    initialBalance = 100
    // Margin_BC = 0
    assert(trading.margin === Fraction(0))
    // Free Margin_BC = 100
    assert(trading.freeMargin === Fraction(100))
    // Profit = 0
    // Equity_BC = 100
    assert(trading.equity === Fraction(100))
    // Margin_Level_% = N/A
    assert(trading.marginLevel === None)

    // **** Entered on buy trade ****
    // 1. Buy 1000 on price = 1.3158:
    verbose("1. Set delayed  buy 1000 on price = 1.3158, when price 1.3160")
    tick("1.3160")

    val executor = trading.newTradeExecutor(new TradeRequest(1000,
                                                             Buy,
                                                             Some(Boundary <= "1.3158")))

    executor.openTrade(Boundary < 1,
                       None,
                       expectCall,
                       failIfCalled)

    // still not opened
    assert(trading.margin === Fraction(0))

    tick("1.3158")


    // Balance_BC = 100
    assert(trading.balance === Fraction(100))
    // Margin_BC = 13.158
    assert(trading.margin === Fraction("13.158"))

    // Free Margin_BC = 86.542
    assert(trading.freeMargin === Fraction("86.542"))
    
    // Profit = -0.3
    assert(trading.profit === Fraction("-0.3"))
    
    // Equity_BC = 99.7
    assert(trading.equity === Fraction("99.7"))
    // Margin_Level_% = 757,714%
    assert(trading.marginLevel === Some(Fraction("7.577139382884936920504635962912297")))

    // **** Profiting price for buy ****
    // 2. Price changed to 1.3171
    verbose("2. Price changed to 1.3171")
    tick("1.3171")

    // Balance_BC = 100
    assert(trading.balance === Fraction(100))
    // Margin_BC = 13.158
    assert(trading.margin === Fraction("13.158"))

    // Free Margin_BC = 87.842
    assert(trading.freeMargin === Fraction("87.842"))
    
    // Profit = 1
    assert(trading.profit === Fraction("1"))
    // Equity_BC = 101
    assert(trading.equity === Fraction("101"))
    
    // Margin_Level_% = 767,594%
    assert(trading.marginLevel === Some(Fraction("7.675938592491260069919440644474844")))

    // **** Lossing price for buy ****
    // 3. Price changed to 1.3100
    verbose("3. Price changed to 1.3100")
    tick("1.31")

    // Balance_BC = 100
    assert(trading.balance === Fraction(100))

    // Margin_BC = 13.158
    assert(trading.margin === Fraction("13.158"))

    // Free Margin_BC = 80.742
    assert(trading.freeMargin === Fraction("80.742"))
    
    // Profit = -6.1
    assert(trading.profit === Fraction("-6.1"))

    // Equity_BC = 93.9
    assert(trading.equity === Fraction("93.9"))

    // Margin_Level_% = 713,634%
    assert(trading.marginLevel === Some(Fraction("7.136342909256725946192430460556316")))

    // **** Closed trade ****
    // 4. Trade closed at 1.3181
    verbose("4. Trade closed at 1.3181")
    tick("1.3181")

    executor.closeTrade
    
    // Balance_BC = 102
    assert(trading.balance === Fraction("102"))

    // Margin_BC = 0
    assert(trading.margin === Fraction(0))

    // Free Margin_BC = 102
    assert(trading.freeMargin === Fraction("102"))

    // Profit = 0
    assert(trading.profit === Fraction(0))
    
    // Equity_BC = 102
    assert(trading.equity === Fraction("102"))
    
    // Margin_Level_% = N/A
    assert(trading.marginLevel === None)
  }

  it should "execute profiting buy without comission" in new Test {
    // ### Profiting buy without comission ###

    // 0. Start conditions:
    verbose("0. Start conditions")
    // Comission = 0
    comissionFactor = 0
    // Balance_BC = 100
    initialBalance = 100
    // Margin_BC = 0
    assert(trading.margin === Fraction(0))
    // Free Margin_BC = 100
    assert(trading.freeMargin === Fraction(100))
    // Profit = 0
    assert(trading.profit === Fraction(0))
    // Equity_BC = 100
    assert(trading.equity === Fraction(100))
    // Margin_Level_% = N/A
    assert(trading.marginLevel === None)

    // **** Entered on buy trade ****
    // 1. Buy 1000 on price = 1.3000:
    verbose("1. Buy 1000 on price = 1.3000")
    tick("1.3")
    
    val executor = trading.newTradeExecutor(new TradeRequest(1000,
                                                             Buy,
                                                             None))

    executor.openTrade(Boundary < 1,
                       None,
                       expectCall,
                       failIfCalled)

    
    // Balance_BC = 100
    assert(trading.balance === Fraction(100))
    
    // Margin_BC = 13.000
    assert(trading.margin === Fraction(13))
    // Free Margin_BC = 87.000
    assert(trading.freeMargin === Fraction(87))
    // Profit = 0
    assert(trading.profit === Fraction(0))
    // Equity_BC = 100
    assert(trading.equity === Fraction(100))
    // Margin_Level_% = 7.69231%
    assert(trading.marginLevel === Some(Fraction("7.692307692307692307692307692307692")))

    // **** Closed trade ****
    // 3. Trade closed at 1.3050
    verbose("3. Trade closed at 1.3050")
    tick("1.305")

    executor.closeTrade

    // Balance_BC = 105
    assert(trading.balance === Fraction("105"))
    
    // Margin_BC = 0
    assert(trading.margin === Fraction(0))

    // Free Margin_BC = 105
    assert(trading.freeMargin === Fraction("105"))
    
    // Profit = 0
    assert(trading.profit === Fraction(0))
    
    // Equity_BC = 105
    assert(trading.equity === Fraction("105"))
    
    // Margin_Level_% = N/A
    assert(trading.marginLevel === None)
  }

  it should "execute delayed profiting buy without comission" in new Test {
    // ### Profiting buy without comission ###

    // 0. Start conditions:
    verbose("0. Start conditions")
    // Comission = 0
    comissionFactor = 0
    // Balance_BC = 100
    initialBalance = 100
    // Margin_BC = 0
    assert(trading.margin === Fraction(0))
    // Free Margin_BC = 100
    assert(trading.freeMargin === Fraction(100))
    // Profit = 0
    assert(trading.profit === Fraction(0))
    // Equity_BC = 100
    assert(trading.equity === Fraction(100))
    // Margin_Level_% = N/A
    assert(trading.marginLevel === None)

    // **** Entered on buy trade ****
    // 1. Buy 1000 on price = 1.3000:
    verbose("1. Buy 1000 on price = 1.3000 (requested when price = 1.4)")
    tick("1.4")
    
    val executor = trading.newTradeExecutor(new TradeRequest(1000,
                                                             Buy,
                                                             Some(Boundary <= "1.3")))

    executor.openTrade(Boundary < 1,
                       None,
                       expectCall,
                       failIfCalled)

    // still not opened
    assert(trading.margin === Fraction(0))

    tick("1.3")
    
    // Balance_BC = 100
    assert(trading.balance === Fraction(100))
    
    // Margin_BC = 13.000
    assert(trading.margin === Fraction(13))
    // Free Margin_BC = 87.000
    assert(trading.freeMargin === Fraction(87))
    // Profit = 0
    assert(trading.profit === Fraction(0))
    // Equity_BC = 100
    assert(trading.equity === Fraction(100))
    // Margin_Level_% = 7.69231%
    assert(trading.marginLevel === Some(Fraction("7.692307692307692307692307692307692")))

    // **** Closed trade ****
    // 3. Trade closed at 1.3050
    verbose("3. Trade closed at 1.3050")
    tick("1.305")

    executor.closeTrade

    // Balance_BC = 105
    assert(trading.balance === Fraction("105"))
    
    // Margin_BC = 0
    assert(trading.margin === Fraction(0))

    // Free Margin_BC = 105
    assert(trading.freeMargin === Fraction("105"))
    
    // Profit = 0
    assert(trading.profit === Fraction(0))
    
    // Equity_BC = 105
    assert(trading.equity === Fraction("105"))
    
    // Margin_Level_% = N/A
    assert(trading.marginLevel === None)
  }


  

  it should "execute losing buy with comission" in new Test {
    // ### Losing buy with comission ###

    // 0. Start conditions:
    verbose("0. Start conditions")
    // Comission = 0.0003
    comissionFactor = "0.0003"
    // Balance_BC = 100
    assert(trading.balance === Fraction(100))
    // Margin_BC = 0
    assert(trading.margin === Fraction(0))
    // Free Margin_BC = 100
    assert(trading.freeMargin === Fraction(100))
    // Profit = 0
    assert(trading.profit === Fraction(0))
    // Equity_BC = 100
    assert(trading.equity === Fraction(100))
    // Margin_Level_% = N/A
    assert(trading.marginLevel === None)

    // **** Entered on buy trade ****
    // 1. Buy 1000 on price = 1.2730:
    verbose("1. Buy 1000 on price = 1.2730")
    tick("1.2730")

    val executor = trading.newTradeExecutor(new TradeRequest(1000,
                                                             Buy,
                                                             None))

    executor.openTrade(Boundary < 1,
                       None,
                       expectCall,
                       failIfCalled)

    // Balance_BC = 100
    assert(trading.balance === Fraction(100))
    // Margin_BC = 12.730
    assert(trading.margin === Fraction("12.730"))

    // Free Margin_BC = 86.970
    assert(trading.freeMargin === Fraction("86.970"))
    
    // Profit = -0.3
    assert(trading.profit === Fraction("-0.3"))
    // Equity_BC = 99.7
    assert(trading.equity === Fraction("99.7"))
    // Margin_Level_% = 7.83189%
    assert(trading.marginLevel === Some(Fraction("7.831893165750196386488609583660644")))

    // **** Closed trade ****
    // 2. Trade closed at 1.2730
    verbose("2. Trade closed at 1.2730")
    tick("1.273")
    executor.closeTrade

    // Balance_BC = 99.7
    assert(trading.balance === Fraction("99.7"))
    // Margin_BC = 0
    assert(trading.margin === Fraction(0))
    // Free Margin_BC = 99.7
    assert(trading.freeMargin === Fraction("99.7"))
    // Profit = 0
    assert(trading.profit === Fraction(0))
    // Equity_BC = 99.7
    assert(trading.equity === Fraction("99.7"))
    // Margin_Level_% = N/A
    assert(trading.marginLevel === None)

  }

  it should "execute losing buy without comission" in new Test {
    // ### Losing buy without comission ###

    // 0. Start conditions:
    verbose("0. Start conditions")
    // Comission = 0
    comissionFactor = 0
    // Balance_BC = 100
    assert(trading.balance === Fraction(100))
    // Margin_BC = 0
    assert(trading.margin === Fraction(0))
    // Free Margin_BC = 100
    assert(trading.freeMargin === Fraction(100))
    // Profit = 0
    assert(trading.profit === Fraction(0))
    // Equity_BC = 100
    assert(trading.equity === Fraction(100))
    // Margin_Level_% = N/A
    assert(trading.marginLevel === None)

    // **** Entered on buy trade ****
    // 1. Buy 1000 on price = 1.0230:
    verbose("1. Buy 1000 on price = 1.0230")
    tick("1.0230")

    val executor = trading.newTradeExecutor(new TradeRequest(1000,
                                                             Buy,
                                                             None))

    executor.openTrade(Boundary < 0,
                       None,
                       expectCall,
                       failIfCalled)

    // Balance_BC = 100
    assert(trading.balance === Fraction(100))
    // Margin_BC = 10.230
    assert(trading.margin === Fraction("10.23"))
    // Free Margin_BC = 89.770
    assert(trading.freeMargin === Fraction("89.770"))
    // Profit = 0
    assert(trading.profit === Fraction(0))
    // Equity_BC = 100
    assert(trading.equity === Fraction(100))
    // Margin_Level_% = 977.517%
    assert(trading.marginLevel === Some(Fraction("9.775171065493646138807429130009775")))

    // **** Closed trade ****
    // 2. Trade closed at 0.9599
    verbose("2. Trade closed at 0.9599")
    tick("0.9599")
    
    executor.closeTrade

    // Balance_BC = 36.9
    assert(trading.balance === Fraction("36.9"))
    // Margin_BC = 0
    assert(trading.margin === Fraction(0))
    // Free Margin_BC = 36.9
    assert(trading.freeMargin === Fraction("36.9"))
    // Profit = 0
    assert(trading.profit === Fraction(0))
    // Equity_BC = 36.9
    assert(trading.equity === Fraction("36.9"))
    // Margin_Level_% = N/A
    assert(trading.marginLevel === None)

  }

  it should "close profiting buy on TakeProfit" in new Test {
    // ### Profiting buy closed on TakePofit###

    // 0. Start conditions:
    verbose("0. Start conditions")

    // Comission = 0
    comissionFactor = 0
    // Balance_BC = 100
    assert(trading.balance === Fraction(100))
    // Margin_BC = 0
    assert(trading.margin === Fraction(0))
    // Free Margin_BC = 100
    assert(trading.freeMargin === Fraction(100))
    // Profit = 0
    assert(trading.profit === Fraction(0))
    // Equity_BC = 100
    assert(trading.equity === Fraction(100))
    // Margin_Level_% = N/A
    assert(trading.marginLevel === None)

    // **** Entered on buy trade ****
    // 1. Buy 1000 on price = 1.2200 with TP >= 1.2210 :
    verbose("1. Buy 1000 on price = 1.2200 with TP >= 1.2210 ")
    tick("1.22")

    val executor = trading.newTradeExecutor(new TradeRequest(1000,
                                                             Buy,
                                                             None))

    executor.openTrade(Boundary < 1,
                       Some(Boundary >= "1.221"),
                       expectCall,
                       expectCall)

    // Balance_BC = 100
    assert(trading.balance === Fraction(100))
    // Margin_BC = 12.200
    assert(trading.margin === Fraction("12.200"))
    // Free Margin_BC = 87.800
    assert(trading.freeMargin === Fraction("87.800"))
    // Profit = 0
    assert(trading.profit === Fraction(0))
    // Equity_BC = 100
    assert(trading.equity === Fraction(100))
    // Margin_Level_% = 819.672%
    assert(trading.marginLevel === Some(Fraction("8.196721311475409836065573770491803")))

    // **** Closed trade ****
    // 2. Trade closed at 1.2212
    verbose("2. Trade closed at 1.2212")

    // according to the #110: Закрытие по тейку
    //
    // it should, actually, be closed by the price of the take profit, to
    // work in more pessimistic way.
    //
    // so, after tick 1.2212, it will close by 1.221
    tick("1.2212")

    // Balance_BC = 101.2
    assert(trading.balance === Fraction("101.0"))
    // Margin_BC = 0
    assert(trading.margin === Fraction(0))
    // Free Margin_BC = 101.2
    assert(trading.freeMargin === Fraction("101.0"))
    // Profit = 0
    assert(trading.profit === Fraction(0))
    // Equity_BC = 101.2
    assert(trading.equity === Fraction("101.0"))
    // Margin_Level_% = N/A
    assert(trading.marginLevel === None)

    executor.closeTrade


    // Balance_BC = 101.2
    assert(trading.balance === Fraction("101.0"))
    // Margin_BC = 0
    assert(trading.margin === Fraction(0))
    // Free Margin_BC = 101.2
    assert(trading.freeMargin === Fraction("101.0"))
    // Profit = 0
    assert(trading.profit === Fraction(0))
    // Equity_BC = 101.2
    assert(trading.equity === Fraction("101.0"))
    // Margin_Level_% = N/A
    assert(trading.marginLevel === None)
  }

  it should "close losing buy on StopLoss" in new Test {
    // ### Losing buy closed on StopLoss ###

    // 0. Start conditions:
    verbose("0. Start conditions")

    // Comission = 0
    comissionFactor = 0
    // Balance_BC = 100
    assert(trading.balance === Fraction(100))
    // Margin_BC = 0
    assert(trading.margin === Fraction(0))
    // Free Margin_BC = 100
    assert(trading.freeMargin === Fraction(100))
    // Profit = 0
    assert(trading.profit === Fraction(0))
    // Equity_BC = 100
    assert(trading.equity === Fraction(100))
    // Margin_Level_% = N/A
    assert(trading.marginLevel === None)

    // **** Entered on buy trade ****
    // 1. Buy 1000 on price = 1.3010 with SL <= 1.3000 :
    verbose("1. Buy 1000 on price = 1.3010 with SL <= 1.3000 ")
    tick("1.301")
    
    val executor = trading.newTradeExecutor(new TradeRequest(1000,
                                                             Buy,
                                                             None))

    executor.openTrade(Boundary <= "1.3",
                       None,
                       expectCall,
                       expectCall)

    // Balance_BC = 100
    assert(trading.balance === Fraction(100))
    // Margin_BC = 13.010
    assert(trading.margin === Fraction("13.010"))
    // Free Margin_BC = 86.990
    assert(trading.freeMargin === Fraction("86.990"))
    // Profit = 0
    assert(trading.profit === Fraction(0))
    // Equity_BC = 100
    assert(trading.equity === Fraction(100))
    // Margin_Level_% = 768.640%
    assert(trading.marginLevel === Some(Fraction("7.686395080707148347425057647963105")))

    // **** Closed trade ****
    // 2. Trade closed at 1.3000
    verbose("2. Trade closed at 1.3000")
    tick("1.3")

    // Balance_BC = 99
    assert(trading.balance === Fraction("99"))
    // Margin_BC = 0
    assert(trading.margin === Fraction(0))
    // Free Margin_BC = 99
    assert(trading.freeMargin === Fraction("99"))
    // Profit = 0
    assert(trading.profit === Fraction(0))
    // Equity_BC = 99
    assert(trading.equity === Fraction("99"))
    // Margin_Level_% = N/A
    assert(trading.marginLevel === None)

  }

  it should "close losing buy on MarginCall (30%)" in new Test {
    // ### Losing buy closed on MarginCall (30%) ###

    // 0. Start conditions:
    verbose("0. Start conditions")

    // Comission = 0
    comissionFactor = 0
    // Balance_BC = 100
    assert(trading.balance === Fraction(100))
    // Margin_BC = 0
    assert(trading.margin === Fraction(0))
    // Free Margin_BC = 100
    assert(trading.freeMargin === Fraction(100))
    // Profit = 0
    assert(trading.profit === Fraction(0))
    // Equity_BC = 100
    assert(trading.equity === Fraction(100))
    // Margin_Level_% = N/A
    assert(trading.marginLevel === None)

    // **** Entered on buy trade ****
    // 1. Buy 1000 on price = 1.2000:
    verbose("1. Buy 1000 on price = 1.2000")
    tick("1.2")

    val executor = trading.newTradeExecutor(new TradeRequest(1000,
                                                             Buy,
                                                             None))

    executor.openTrade(Boundary < 1,
                       None,
                       expectCall,
                       expectCall)

    // Balance_BC = 100
    assert(trading.balance === Fraction(100))
    // Margin_BC = 12.000
    assert(trading.margin === Fraction("12.000"))
    // Free Margin_BC = 88.000
    assert(trading.freeMargin === Fraction("88.000"))
    // Profit = 0
    assert(trading.profit === Fraction(0))
    // Equity_BC = 100
    assert(trading.equity === Fraction(100))
    // Margin_Level_% = 833.333%
    assert(trading.marginLevel === Some(Fraction("8.333333333333333333333333333333333")))

    // **** Closed trade ****
    // 2. Trade closed at 1.1036 by MarginCall
    verbose("2. Trade closed at 1.1036 by MarginCall")
    tick("1.1036")

    // Balance_BC = 3.6
    assert(trading.balance === Fraction("3.6"))
    // Margin_BC = 0
    assert(trading.margin === Fraction(0))
    // Free Margin_BC = 3.6
    assert(trading.freeMargin === Fraction("3.6"))
    // Profit = 0
    assert(trading.profit === Fraction(0))
    // Equity_BC = 3.6
    assert(trading.equity === Fraction("3.6"))
    // Margin_Level_% = N/A
    assert(trading.marginLevel === None)

  }

  it should "not open buy due to lack of Free Margin" in new Test {
    // ### Buy is not opened due to lack of Free Margin ###

    // 0. Start conditions:
    verbose("0. Start conditions")

    // Comission = 0
    comissionFactor = 0
    // Balance_BC = 100
    assert(trading.balance === Fraction(100))
    // Margin_BC = 0
    assert(trading.margin === Fraction(0))
    // Free Margin_BC = 100
    assert(trading.freeMargin === Fraction(100))
    // Profit = 0
    assert(trading.profit === Fraction(0))
    // Equity_BC = 100
    assert(trading.equity === Fraction(100))
    // Margin_Level_% = N/A
    assert(trading.marginLevel === None)

    // **** Entered on buy trade ****
    // 1. Buy 10000 on price = 1.2000:
    verbose("1. Buy 10000 on price = 1.2000")
    // Trade is not opened (TradeMargin=120 > FreeMargin = 100)

    tick("1.2")

    val executor = trading.newTradeExecutor(new TradeRequest(10000,
                                                             Sell,
                                                             None))
    intercept[SimulationError] {
      executor.openTrade(Boundary <= 1,
                         None,
                         failIfCalled,
                         failIfCalled)
    }
    
  }

  it should "open buy on all Free Margin" in new Test {
    // ### Buy is opened on all Free Margin ###

    // 0. Start conditions:
    verbose("0. Start conditions")

    // Comission = 0
    comissionFactor = 0
    // Balance_BC = 100
    assert(trading.balance === Fraction(100))
    // Margin_BC = 0
    assert(trading.margin === Fraction(0))
    // Free Margin_BC = 100
    assert(trading.freeMargin === Fraction(100))
    // Profit = 0
    assert(trading.profit === Fraction(0))
    // Equity_BC = 100
    assert(trading.equity === Fraction(100))
    // Margin_Level_% = N/A
    assert(trading.marginLevel === None)

    // **** Entered on buy trade ****
    // 1. Buy 10000 on price = 1.0000:
    verbose("1. Buy 10000 on price = 1.0000")
    tick("1")

    val executor = trading.newTradeExecutor(new TradeRequest(10000,
                                                             Buy,
                                                             None))

    executor.openTrade(Boundary < 1,
                       None,
                       expectCall,
                       failIfCalled)

    // Balance_BC = 100
    assert(trading.balance === Fraction(100))
    // Margin_BC = 100
    assert(trading.margin === Fraction(100))
    // Free Margin_BC = 0
    assert(trading.freeMargin === Fraction(0))
    // Profit = 0
    assert(trading.profit === Fraction(0))
    // Equity_BC = 100
    assert(trading.equity === Fraction(100))
    // Margin_Level_% = 100%
    assert(trading.marginLevel === Some(Fraction(1)))
  }

  it should "execute profiting sell with comission" in new Test {
    // ### Profiting sell with comission ###

    // 0. Start conditions:
    verbose("0. Start conditions")

    // Comission = 0.0003
    comissionFactor = "0.0003"
    // Balance_BC = 100
    assert(trading.balance === Fraction(100))
    // Margin_BC = 0
    assert(trading.margin === Fraction(0))
    // Free Margin_BC = 100
    assert(trading.freeMargin === Fraction(100))
    // Profit = 0
    assert(trading.profit === Fraction(0))
    // Equity_BC = 100
    assert(trading.equity === Fraction(100))
    // Margin_Level_% = N/A
    assert(trading.marginLevel === None)

    // **** Entered on sell trade ****
    // 1. Sell 1000 on price = 1.3158:
    verbose("1. Sell 1000 on price = 1.3158")
    tick("1.3158")

    val executor = trading.newTradeExecutor(new TradeRequest(1000,
                                                             Sell,
                                                             None))

    executor.openTrade(Boundary > 2,
                       None,
                       expectCall,
                       failIfCalled)

    // Balance_BC = 100
    assert(trading.balance === Fraction(100))
    // Margin_BC = 13.158
    assert(trading.margin === Fraction("13.158"))

    // Free Margin_BC = 86.542
    assert(trading.freeMargin === Fraction("86.542"))
    
    // Profit = -0.3
    assert(trading.profit === Fraction("-0.3"))
    // Equity_BC = 99.7
    assert(trading.equity === Fraction("99.7"))
    // Margin_Level_% = 757,714%
    assert(trading.marginLevel === Some(Fraction("7.577139382884936920504635962912297")))

    // **** Profiting price for sell ****
    // 2. Price changed to 1.3100
    verbose("2. Price changed to 1.3100")
    tick("1.31")

    // Balance_BC = 100
    assert(trading.balance === Fraction(100))
    // Margin_BC = 13.158
    assert(trading.margin === Fraction("13.158"))
    // Free Margin_BC = 80.742
    assert(trading.freeMargin === Fraction("92.342"))
    // Profit = 5.5
    assert(trading.profit === Fraction("5.5"))
    // Equity_BC = 105.5
    assert(trading.equity === Fraction("105.5"))
    // Margin_Level_% = 801,794%
    assert(trading.marginLevel === Some(Fraction("8.017935856513147894816841465268278")))

    // **** Losing price for sell ****
    // 3. Price changed to 1.3171
    verbose("3. Price changed to 1.3171 ")
    tick("1.3171")

    // Balance_BC = 100
    assert(trading.balance === Fraction(100))
    // Margin_BC = 13.158
    assert(trading.margin === Fraction("13.158"))
    // Free Margin_BC = 87.842
    assert(trading.freeMargin === Fraction("85.242"))
    // Profit = -1.6
    assert(trading.profit === Fraction("-1.6"))
    // Equity_BC = 98.4
    assert(trading.equity === Fraction("98.4"))
    // Margin_Level_% = 747,834%
    assert(trading.marginLevel === Some(Fraction("7.478340173278613771089831281349749")))

    // **** Closed trade ****
    // 4. Trade closed at 1.3158
    verbose("4. Trade closed at 1.3158")
    tick("1.3158")

    executor.closeTrade

    // Balance_BC = 99.7
    assert(trading.balance === Fraction("99.7"))
    // Margin_BC = 0
    assert(trading.margin === Fraction(0))
    // Free Margin_BC = 99.7
    assert(trading.freeMargin === Fraction("99.7"))
    // Profit = 0
    assert(trading.profit === Fraction(0))
    // Equity_BC = 99.7
    assert(trading.equity === Fraction("99.7"))
    // Margin_Level_% = N/A
    assert(trading.marginLevel === None)

  }

  it should "execute profiting sell without comission" in new Test {
    // ### Profiting sell without comission ###

    // 0. Start conditions:
    verbose("0. Start conditions")

    // Comission = 0
    comissionFactor = 0
    // Balance_BC = 100
    assert(trading.balance === Fraction(100))
    // Margin_BC = 0
    assert(trading.margin === Fraction(0))
    // Free Margin_BC = 100
    assert(trading.freeMargin === Fraction(100))
    // Profit = 0
    assert(trading.profit === Fraction(0))
    // Equity_BC = 100
    assert(trading.equity === Fraction(100))
    // Margin_Level_% = N/A
    assert(trading.marginLevel === None)

    // **** Entered on sell trade ****
    // 1. Sell 1000 on price = 1.3000:
    verbose("1. Sell 1000 on price = 1.3000")
    tick("1.3")
    
    val executor = trading.newTradeExecutor(new TradeRequest(1000,
                                                             Sell,
                                                             None))

    executor.openTrade(Boundary > 2,
                       None,
                       expectCall,
                       failIfCalled)

    // Balance_BC = 100
    assert(trading.balance === Fraction(100))
    // Margin_BC = 13.000
    assert(trading.margin === Fraction("13.000"))
    // Free Margin_BC = 87.000
    assert(trading.freeMargin === Fraction("87.000"))
    // Profit = 0
    assert(trading.profit === Fraction(0))
    // Equity_BC = 100
    assert(trading.equity === Fraction(100))
    // Margin_Level_% = 769,231%
    assert(trading.marginLevel === Some(Fraction("7.692307692307692307692307692307692")))

    // **** Closed trade ****
    // 2. Trade closed at 1.2950
    verbose("2. Trade closed at 1.2950")
    tick("1.295")
    executor.closeTrade

    // Balance_BC = 105
    assert(trading.balance === Fraction("105."))
    // Margin_BC = 0
    assert(trading.margin === Fraction(0))
    // Free Margin_BC = 105
    assert(trading.freeMargin === Fraction("105."))
    // Profit = 0
    assert(trading.profit === Fraction(0))
    // Equity_BC = 105
    assert(trading.equity === Fraction("105."))
    // Margin_Level_% = N/A
    assert(trading.marginLevel === None)

  }

  it should "execute losing sell with comission" in new Test {
    // ### Losing sell with comission ###

    // 0. Start conditions:
    verbose("0. Start conditions")

    // Comission = 0.0003
    comissionFactor = "0.0003"
    // Balance_BC = 100
    assert(trading.balance === Fraction(100))
    // Margin_BC = 0
    assert(trading.margin === Fraction(0))
    // Free Margin_BC = 100
    assert(trading.freeMargin === Fraction(100))
    // Profit = 0
    assert(trading.profit === Fraction(0))
    // Equity_BC = 100
    assert(trading.equity === Fraction(100))
    // Margin_Level_% = N/A
    assert(trading.marginLevel === None)

    // **** Entered on sell trade ****
    // 1. Sell 1000 on price = 1.2730:
    verbose("1. Sell 1000 on price = 1.2730")
    tick("1.2730")

    val executor = trading.newTradeExecutor(new TradeRequest(1000,
                                                             Sell,
                                                             None))

    executor.openTrade(Sell.stop(2),
                       None,
                       expectCall,
                       failIfCalled)
    
    // Balance_BC = 100
    assert(trading.balance === Fraction(100))
    // Margin_BC = 12.730
    assert(trading.margin === Fraction("12.730"))

    // Free Margin_BC = 86.970
    assert(trading.freeMargin === Fraction("86.970"))


    // Profit = -0.3
    assert(trading.profit === Fraction("-0.3"))
    // Equity_BC = 99.7
    assert(trading.equity === Fraction("99.7"))
    // Margin_Level_% = 783,189%
    assert(trading.marginLevel === Some(Fraction("7.831893165750196386488609583660644")))

    // **** Closed trade ****
    // 2. Trade closed at 1.2732
    verbose("2. Trade closed at 1.2732")
    tick("1.2732")

    executor.closeTrade

    // Balance_BC = 99.5
    assert(trading.balance === Fraction("99.5"))
    // Margin_BC = 0
    assert(trading.margin === Fraction(0))
    // Free Margin_BC = 99.5
    assert(trading.freeMargin === Fraction("99.5"))
    // Profit = 0
    assert(trading.profit === Fraction(0))
    // Equity_BC = 99.5
    assert(trading.equity === Fraction("99.5"))
    // Margin_Level_% = N/A
    assert(trading.marginLevel === None)

  }

  it should "execute losing sell without comission" in new Test {
    // ### Losing sell without comission ###

    // 0. Start conditions:
    verbose("0. Start conditions")

    // Comission = 0
    comissionFactor = 0
    // Balance_BC = 100
    assert(trading.balance === Fraction(100))
    // Margin_BC = 0
    assert(trading.margin === Fraction(0))
    // Free Margin_BC = 100
    assert(trading.freeMargin === Fraction(100))
    // Profit = 0
    assert(trading.profit === Fraction(0))
    // Equity_BC = 100
    assert(trading.equity === Fraction(100))
    // Margin_Level_% = N/A
    assert(trading.marginLevel === None)

    // **** Entered on sell trade ****
    // 1. Sell 1000 on price = 1.0230:
    verbose("1. Sell 1000 on price = 1.0230")
    tick("1.023")
    
    val executor = trading.newTradeExecutor(new TradeRequest(1000,
                                                             Sell,
                                                             None))

    executor.openTrade(Sell.stop(2),
                       None,
                       expectCall,
                       failIfCalled)

    // Balance_BC = 100
    assert(trading.balance === Fraction(100))
    // Margin_BC = 10.230
    assert(trading.margin === Fraction("10.23"))
    // Free Margin_BC = 89.770
    assert(trading.freeMargin === Fraction("89.770"))
    // Profit = 0
    assert(trading.profit === Fraction(0))
    // Equity_BC = 100
    assert(trading.equity === Fraction(100))
    // Margin_Level_% = 977,517%
    assert(trading.marginLevel === Some(Fraction("9.775171065493646138807429130009775")))

    // **** Closed trade ****
    // 2. Trade closed at 1.0260
    verbose("2. Trade closed at 1.0260")
    tick("1.026")

    executor.closeTrade

    // Balance_BC = 97
    assert(trading.balance === Fraction("97"))
    // Margin_BC = 0
    assert(trading.margin === Fraction(0))
    // Free Margin_BC = 97
    assert(trading.freeMargin === Fraction("97"))
    // Profit = 0
    assert(trading.profit === Fraction(0))
    // Equity_BC = 97
    assert(trading.equity === Fraction("97"))
    // Margin_Level_% = N/A
    assert(trading.marginLevel === None)

  }

  it should "close profiting sell on TakeProfit" in new Test {
    // ### Profiting sell closed on TakePofit###

    // 0. Start conditions:
    verbose("0. Start conditions")

    // Comission = 0
    comissionFactor = 0
    // Balance_BC = 100
    assert(trading.balance === Fraction(100))
    // Margin_BC = 0
    assert(trading.margin === Fraction(0))
    // Free Margin_BC = 100
    assert(trading.freeMargin === Fraction(100))
    // Profit = 0
    assert(trading.profit === Fraction(0))
    // Equity_BC = 100
    assert(trading.equity === Fraction(100))
    // Margin_Level_% = N/A
    assert(trading.marginLevel === None)

    // **** Entered on sell trade ****
    // 1. Sell 1000 on price = 1.2200 with TP <= 1.2100 :
    verbose("1. Sell 1000 on price = 1.2200 with TP <= 1.2100 ")
    tick("1.22")

    val executor = trading.newTradeExecutor(new TradeRequest(1000,
                                                             Sell,
                                                             None))

    executor.openTrade(Sell.stop(2),
                       Some(Boundary <= "1.21"),
                       expectCall,
                       expectCall)

    // Balance_BC = 100
    assert(trading.balance === Fraction(100))
    // Margin_BC = 12.200
    assert(trading.margin === Fraction("12.200"))
    // Free Margin_BC = 87.800
    assert(trading.freeMargin === Fraction("87.800"))
    // Profit = 0
    assert(trading.profit === Fraction(0))
    // Equity_BC = 100
    assert(trading.equity === Fraction(100))
    // Margin_Level_% = 819,672%
    assert(trading.marginLevel === Some(Fraction("8.196721311475409836065573770491803")))

    // **** Closed trade ****
    // 2. Trade closed at 1.2100
    verbose("2. Trade closed at 1.2100")
    tick("1.21")

    // Balance_BC = 110
    assert(trading.balance === Fraction("110"))
    // Margin_BC = 0
    assert(trading.margin === Fraction(0))
    // Free Margin_BC = 110
    assert(trading.freeMargin === Fraction("110"))
    // Profit = 0
    assert(trading.profit === Fraction(0))
    // Equity_BC = 110
    assert(trading.equity === Fraction("110"))
    // Margin_Level_% = N/A
    assert(trading.marginLevel === None)

  }

  it should "close losing sell on StopLoss" in new Test {
    // ### Losing sell closed on StopLoss ###

    // 0. Start conditions:
    verbose("0. Start conditions")
    // Comission = 0
    comissionFactor = 0
    // Balance_BC = 100
    assert(trading.balance === Fraction(100))
    // Margin_BC = 0
    assert(trading.margin === Fraction(0))
    // Free Margin_BC = 100
    assert(trading.freeMargin === Fraction(100))
    // Profit = 0
    assert(trading.profit === Fraction(0))
    // Equity_BC = 100
    assert(trading.equity === Fraction(100))
    // Margin_Level_% = N/A
    assert(trading.marginLevel === None)

    // **** Entered on sell trade ****
    // 1. Sell 1000 on price = 1.3010 with SL <= 1.3020 :
    verbose("1. Sell 1000 on price = 1.3010 with SL >= 1.3020 ")

    tick("1.3010")
    
    val executor = trading.newTradeExecutor(new TradeRequest(1000,
                                                             Sell,
                                                             None))

    executor.openTrade(Boundary >= "1.302",
                       None,
                       expectCall,
                       expectCall)

    // Balance_BC = 100
    assert(trading.balance === Fraction(100))
    // Margin_BC = 13.010
    assert(trading.margin === Fraction("13.010"))
    // Free Margin_BC = 86.990
    assert(trading.freeMargin === Fraction("86.990"))
    // Profit = 0
    assert(trading.profit === Fraction(0))
    // Equity_BC = 100
    assert(trading.equity === Fraction(100))
    // Margin_Level_% = 768.640%))
    assert(trading.marginLevel === Some(Fraction("7.686395080707148347425057647963105")))

    // **** Closed trade ****
    // 2. Trade closed at 1.3025
    verbose("2. Trade closed at 1.3025")
    tick("1.3025")

    // Balance_BC = 98.5
    assert(trading.balance === Fraction("98.5"))
    // Margin_BC = 0
    assert(trading.margin === Fraction(0))
    // Free Margin_BC = 98.5
    assert(trading.freeMargin === Fraction("98.5"))
    // Profit = 0
    assert(trading.profit === Fraction(0))
    // Equity_BC = 98.5
    assert(trading.equity === Fraction("98.5"))
    // Margin_Level_% = N/A
    assert(trading.marginLevel === None)

  }

  it should "close losing sell by margin call (30%)" in new Test {
    // ### Losing sell closed on MarginCall (30%) ###


    // 0. Start conditions:
    verbose("0. Start conditions")

    // Comission = 0
    comissionFactor = 0
    // Balance_BC = 100
    assert(trading.balance === Fraction(100))
    // Margin_BC = 0
    assert(trading.margin === Fraction(0))
    // Free Margin_BC = 100
    assert(trading.freeMargin === Fraction(100))
    // Profit = 0
    assert(trading.profit === Fraction(0))
    // Equity_BC = 100
    assert(trading.equity === Fraction(100))
    // Margin_Level_% = N/A
    assert(trading.marginLevel === None)

    // **** Entered on sell trade ****
    // 1. Sell 1000 on price = 1.2000:
    verbose("1. Sell 1000 on price = 1.2000")
    tick("1.2")
    val executor = trading.newTradeExecutor(new TradeRequest(1000,
                                                             Sell,
                                                             None))

    executor.openTrade(Sell.stop(2),
                       None,
                       expectCall,
                       expectCall)

    // Balance_BC = 100
    assert(trading.balance === Fraction(100))
    // Margin_BC = 12.000
    assert(trading.margin === Fraction("12.000"))
    // Free Margin_BC = 88.000
    assert(trading.freeMargin === Fraction("88.000"))
    // Profit = 0
    assert(trading.profit === Fraction(0))
    // Equity_BC = 100
    assert(trading.equity === Fraction(100))
    // Margin_Level_% = 833.333%
    assert(trading.marginLevel === Some(Fraction("8.333333333333333333333333333333333")))


    // **** Closed trade ****
    // 2. Trade closed at 1.2965 by MarginCall
    verbose("2. Trade closed at 1.2965 by MarginCall")
    tick("1.2965")

    // Balance_BC = 3.5
    assert(trading.balance === Fraction("3.5"))
    // Margin_BC = 0
    assert(trading.margin === Fraction(0))
    // Free Margin_BC = 3.5
    assert(trading.freeMargin === Fraction("3.5"))
    // Profit = 0
    assert(trading.profit === Fraction(0))
    // Equity_BC = 3.5
    assert(trading.equity === Fraction("3.5"))
    // Margin_Level_% = N/A
    assert(trading.marginLevel === None)
  }

  it should "dont open sell due to lack of Free Margin" in new Test {
    // ### Sell is not opened due to lack of Free Margin ###

    // 0. Start conditions:
    verbose("0. Start conditions")

    // Comission = 0
    comissionFactor = 0
    // Balance_BC = 100
    assert(trading.balance === Fraction(100))
    // Margin_BC = 0
    assert(trading.margin === Fraction(0))
    // Free Margin_BC = 100
    assert(trading.freeMargin === Fraction(100))
    // Profit = 0
    assert(trading.profit === Fraction(0))
    // Equity_BC = 100
    assert(trading.equity === Fraction(100))
    // Margin_Level_% = N/A
    assert(trading.marginLevel === None)

    // **** Entered on sell trade ****
    // 1. Buy 10000 on price = 1.2000:
    verbose("1. Buy 10000 on price = 1.2000")
    tick("1.2")

    val executor = trading.newTradeExecutor(new TradeRequest(10000,
                                                             Sell,
                                                             None))
    intercept[SimulationError] {
      executor.openTrade(Boundary <= 1,
                         None,
                         failIfCalled,
                         failIfCalled)
    }
  }

  it should "open sell on all Free Margin" in new Test {
    // ### Sell is opened on all Free Margin ###

    // 0. Start conditions:
    verbose("0. Start conditions")

    // Comission = 0
    comissionFactor = 0
    // Balance_BC = 100
    assert(trading.balance === Fraction(100))
    // Margin_BC = 0
    assert(trading.margin === Fraction(0))
    // Free Margin_BC = 100
    assert(trading.freeMargin === Fraction(100))
    // Profit = 0
    assert(trading.profit === Fraction(0))
    // Equity_BC = 100
    assert(trading.equity === Fraction(100))
    // Margin_Level_% = N/A
    assert(trading.marginLevel === None)

    // **** Entered on sell trade ****
    // 1. Sell 10000 on price = 1.0000:
    verbose("1. Sell 10000 on price = 1.0000")
    tick("1")

    val executor = trading.newTradeExecutor(new TradeRequest(10000,
                                                             Sell,
                                                             None))

    executor.openTrade(Sell.stop(2),
                       None,
                       expectCall,
                       failIfCalled)

    // Balance_BC = 100
    assert(trading.balance === Fraction(100))
    // Margin_BC = 100
    assert(trading.margin === Fraction(100))
    // Free Margin_BC = 0
    assert(trading.freeMargin === Fraction(0))
    // Profit = 0
    assert(trading.profit === Fraction(0))
    // Equity_BC = 100
    assert(trading.equity === Fraction(100))
    // Margin_Level_% = 100%
    assert(trading.marginLevel === Some(Fraction(1)))
  }

  def testTpStopCrossingTradeOpen(tradeType:Trade) = {
    it should ("throw SimulationError opening " + tradeType + " if stop crossed") in new Test {

      override def leverage = Fraction("1")

      initialBalance = 10000

      tick(1)

      val executor = trading.newTradeExecutor(new TradeRequest(10000,
                                                               tradeType,
                                                               None))

      intercept[SimulationError] {
        executor.openTrade(Boundary <= 1,
                           None,
                           failIfCalled,
                           failIfCalled)
      }
    }

    it should ("throw SimulationError opening " + tradeType + " if take profit crossed") in new Test {
      override def leverage = Fraction("1")

      initialBalance = 10000

      tick(1)

      val executor = trading.newTradeExecutor(new TradeRequest(10000,
                                                               tradeType,
                                                               None))

      intercept[SimulationError] {
        executor.openTrade(Boundary < 1,
                           Some(Boundary < 2),
                           failIfCalled,
                           failIfCalled)
      }
    }
  }

  testTpStopCrossingTradeOpen(Buy)
  testTpStopCrossingTradeOpen(Sell)

  it should "throw SimulationError if stop changed and new stop is crossed" in new Test {

    override def leverage = Fraction("1")

    initialBalance = 10000
    
    tick(1)

    val executor = trading.newTradeExecutor(new TradeRequest(10000,
                                                             Sell,
                                                             None))

    executor.openTrade(Sell.stop(2),
                       None,
                       expectCall,
                       failIfCalled)

    assert(trading.activeTrades === 1)

    intercept[SimulationError] {
      executor.setStop(Boundary <= 1)
    }
    
  }

  it should "throw SimulationError if tp changed and new tp is crossed" in new Test {
    override def leverage = Fraction("1")

    initialBalance = 10000
    
    tick(1)

    val executor = trading.newTradeExecutor(new TradeRequest(10000,
                                                             Sell,
                                                             None))

    executor.openTrade(Sell.stop(2),
                       None,
                       expectCall,
                       failIfCalled)

    assert(trading.activeTrades === 1)

    intercept[SimulationError] {
      executor.setTakeProfit(Some(Boundary <= 1))
    }
    
  }

  it should "throw SimulationError if new trade request with 0 value" in new Test {
    override def leverage = Fraction("1")

    intercept[SimulationError] {
      trading.newTradeExecutor(new TradeRequest(0,
                                                Sell,
                                                None))
    }
  }

  it should "throw SimulationError if new trade request with value < 0" in new Test {
    override def leverage = Fraction("1")

    intercept[SimulationError] {
      trading.newTradeExecutor(new TradeRequest(-1,
                                                Sell,
                                                None))
    }
  }

  it should "throw SimulationError if stop changed so it crossed now" in new Test {
    override def leverage = Fraction("1000")

    initialBalance = 100
    tick(1)

    val executor = trading.newTradeExecutor(new TradeRequest(100,
                                                             Buy,
                                                             None))

    executor.openTrade(Boundary < 1,
                       None,
                       expectCall,
                       failIfCalled)
    
    val error = intercept[SimulationError] {
        executor.setStop(Boundary <= 1)
      }

    assert(error.message === "New stop is invalid - it crossed at current price")
  }

  it should "throw SimulationError if tp changed so it crossed now" in new Test {
    override def leverage = Fraction("1000")

    initialBalance = 100
    tick(1)

    val executor = trading.newTradeExecutor(new TradeRequest(100,
                                                             Buy,
                                                             None))

    executor.openTrade(Boundary < 1,
                       None,
                       expectCall,
                       failIfCalled)
    
    val error = intercept[SimulationError] {
        executor.setTakeProfit(Some(Boundary <= 1))
      }

    assert(error.message === "New take profit is invalid - it crossed at current price")
  }

  it should "disable trade, not throw error and log message if delayed and stop executes at same time" in new Test{
    // 0. Start conditions:
    verbose("0. Start conditions")
    // Comission = 0
    comissionFactor = 0
    // Balance_BC = 100
    initialBalance = 100
    // Margin_BC = 0
    assert(trading.margin === Fraction(0))
    // Free Margin_BC = 100
    assert(trading.freeMargin === Fraction(100))
    // Profit = 0
    assert(trading.profit === Fraction(0))
    // Equity_BC = 100
    assert(trading.equity === Fraction(100))
    // Margin_Level_% = N/A
    assert(trading.marginLevel === None)

    // **** Entered on buy trade ****
    // 1. Buy 1000 on price = 1.3000:
    verbose("1. Buy 1000 on price = 1.3000 (requested when price = 1.4)")
    tick("1.4")
    
    val executor = trading.newTradeExecutor(new TradeRequest(1000,
                                                             Buy,
                                                             Some(Boundary <= "1.3")))

    executor.openTrade(Boundary < 1,
                       None,
                       failIfCalled,
                       failIfCalled)

    // still not opened
    assert(trading.margin === Fraction(0))

    verbose("2. price changed to 0.9")
    
    tick("0.9")

    verbose("3. should not open trade")

    // not opened
    assert(trading.margin === Fraction(0))

    verbose("4. price changed to 1.1")

    tick("1.1")

    verbose("5. should still not open trade")

    // still not opened
    assert(trading.margin === Fraction(0))
  }

  it should "throw error if delay and tp are of same direction" in new Test {
    // 0. Start conditions:
    verbose("0. Start conditions")

    // Comission = 0
    comissionFactor = 0
    // Balance_BC = 100
    initialBalance = 100
    // Margin_BC = 0
    assert(trading.margin === Fraction(0))
    // Free Margin_BC = 100
    assert(trading.freeMargin === Fraction(100))
    // Profit = 0
    assert(trading.profit === Fraction(0))
    // Equity_BC = 100
    assert(trading.equity === Fraction(100))
    // Margin_Level_% = N/A
    assert(trading.marginLevel === None)

    // **** Entered on buy trade ****
    // 1. Buy 1000 on price = 1.3000:
    verbose("1. Buy 1000 on price = 1.3000 (requested when price = 1.4)")
    tick("1.4")
    
    val executor = trading.newTradeExecutor(new TradeRequest(1000,
                                                             Buy,
                                                             Some(Boundary <= "1.3")))

    intercept [SimulationError] {
      executor.openTrade(Boundary < "0.2",
                         Some(Boundary < 1),
                         failIfCalled,
                         failIfCalled)
    }
  }

  it should "correctly process tp crossing current price, but not crossing expected open price:\n  1. trade is delayed \n  2. tp crossed at current price\n  2. tp not crossed on expected open price" in new Test {


    tick("1.4")

    val executor = trading.newTradeExecutor(new TradeRequest(1000,
                                                             Buy,
                                                             Some(Boundary <= "1.3")))

    executor.openTrade(Boundary <= "1.25",
                       Some(Boundary >= "1.35"),
                       failIfCalled,
                       failIfCalled)
  }

  it should "invalidate stop for opened trades" in new Test {
    intercept[SimulationError] {

      tick("1.4")

      val executor = trading.newTradeExecutor(new TradeRequest(1000,
                                                               Buy,
                                                               Some(Boundary <= "1.3")))

      executor.openTrade(Boundary <= "1.25",
                         Some(Boundary >= "1.35"),
                         expectCall,
                         failIfCalled)

      tick("1.3")

      executor.setStop(Boundary <= "1.3")
    }
  }

  it should "invalidate tp for opened trades" in new Test {
    intercept[SimulationError] {
      tick("1.4")

      val executor = trading.newTradeExecutor(new TradeRequest(1000,
                                                               Buy,
                                                               Some(Boundary <= "1.3")))

      executor.openTrade(Boundary <= "1.25",
                         Some(Boundary >= "1.35"),
                         expectCall,
                         failIfCalled)

      tick("1.3")

      executor.setTakeProfit(Some(Boundary >= "1.3"))
    }
  }

  it should "not throw error if tp boundary have same value but other direction with delay" in new Test {

    tick("1.4")

    val executor = trading.newTradeExecutor(new TradeRequest(1000,
                                                             Buy,
                                                             Some(Boundary <= "1.3")))

    executor.openTrade(Boundary <= "1.25",
                       Some(Boundary > "1.3"),
                       failIfCalled,
                       failIfCalled)
  }

  def testSettingStopAndTpIfLimitedByDistance(tradeType:Trade) {

    val basePrice = Fraction("1.4")

    def shiftedPrice(shift:Fraction) = basePrice - tradeType * shift


    it should ("set stop for opened trade not closer than configured stop distance for " + tradeType) in new Test {
      override def limits = new IndependentStops(Fraction("0.001"),
                                                 0,
                                                 0,
                                                 0)


      tick(basePrice)

      val executor = trading.newTradeExecutor(new TradeRequest(1000,
                                                               tradeType,
                                                               None))

      executor.openTrade(tradeType.stop(basePrice,
                                        "0.0001"),
                         None,
                         expectCall,
                         expectCall)
      // for buy:
      // trade should be opened and should not be closed by stop on
      // price 1.3998 because stop should be set to 1.399

      tick(shiftedPrice("0.0002"))

      // if trade not closed - balance not changed
      assert(trading.balance === initialBalance)

      tick(shiftedPrice("0.001"))

      assert(trading.balance != initialBalance)
    }

    it should ("/-/-/-/ and trail stop closer to the requested value for " + tradeType) in new Test {
      override def limits = new IndependentStops(Fraction("0.001"),
                                                 0,
                                                 0,
                                                 0)

      tick(basePrice)

      val executor = trading.newTradeExecutor(new TradeRequest(1000,
                                                               tradeType,
                                                               None))

      executor.openTrade(tradeType.stop(basePrice,
                                        "0.0001"),
                         None,
                         expectCall,
                         expectCall)

      // stop should be set to 1.399, trade should not close

      tick(shiftedPrice("0.0002"))
      tick(shiftedPrice("0.0008"))

      // now price reverts and goes to the opposite direction, allowing stop
      // to trail it closer to the requested position

      // if trade not closed - balance not changed
      assert(trading.balance === initialBalance)

      tick(shiftedPrice("0.0003"))
      tick(shiftedPrice("0"))
      tick(shiftedPrice("-0.0001"))
      tick(shiftedPrice("-0.0005"))

      // there stop should be trailed to the 1.3995

      // so, still not closed
      tick(shiftedPrice("0.0004"))

      assert(trading.balance === initialBalance)

      // and this price change should cause closing trade
      tick(shiftedPrice("0.0005"))

      assert(trading.balance != initialBalance)
    }

    it should ("/-/-/-/ and trail stop the requested value for "  + tradeType) in new Test {
      override def limits = new IndependentStops(Fraction("0.001"),
                                                 0,
                                                 0,
                                                 0)

      tick(basePrice)

      val executor = trading.newTradeExecutor(new TradeRequest(1000,
                                                               tradeType,
                                                               None))

      executor.openTrade(tradeType.stop(basePrice,
                                        "0.0001"),
                         None,
                         expectCall,
                         expectCall)

      // stop should be set to 1.399, trade should not close

      tick(shiftedPrice("0.0002"))
      tick(shiftedPrice("0.0008"))

      // now price reverts and goes to the opposite direction, allowing stop
      // to trail it closer to the requested position

      // if trade not closed - balance not changed
      assert(trading.balance === initialBalance)

      tick(shiftedPrice("0.0003"))
      tick(shiftedPrice("0"))
      tick(shiftedPrice("-0.0001"))
      tick(shiftedPrice("-0.0005"))
      tick(shiftedPrice("-0.0010"))
      tick(shiftedPrice("-0.0012"))

      // there stop should be trailed to the 1.3999, as initially requested

      // so, still not closed
      tick(shiftedPrice("0"))

      assert(trading.balance === initialBalance)

      // and this price change should cause closing trade
      tick(shiftedPrice("0.0001"))

      assert(trading.balance != initialBalance)
    }

    it should ("count stop distance for limit from expected open price for "  + tradeType) in new Test {
      override def limits = new IndependentStops(Fraction("0.001"),
                                                 0,
                                                 0,
                                                 0)

      tick(basePrice)

      val executor = trading.newTradeExecutor(new TradeRequest(1000,
                                                               tradeType,
                                                               Some(tradeType.delay(basePrice,
                                                                                    "0.0005"))))

      executor.openTrade(tradeType.stop(shiftedPrice("0.0005"),
                                        "0.0001"),
                         None,
                         expectCall,
                         expectCall)

      // stop should be set to 1.3985, because of delay

      tick(shiftedPrice("0.0002"))

      // after this tick, trade should open by price 1.3992,
      // and stop is set to the 1.3985, because it set from the expected price
      tick(shiftedPrice("0.0008"))

      // and it should tno close on price 1.3986
      tick(shiftedPrice("0.0014"))

      assert(trading.balance === initialBalance)

      // and close by stop on price 1.3985
      tick(shiftedPrice("0.0015"))

      assert(trading.balance != initialBalance)
    }

    it should ("/-/-/-/-/ for "  + tradeType + " and trail stop after trade opened") in new Test {
      override def limits = new IndependentStops(Fraction("0.001"),
                                                 0,
                                                 0,
                                                 0)

      tick(basePrice)

      val executor = trading.newTradeExecutor(new TradeRequest(1000,
                                                               tradeType,
                                                               Some(tradeType.delay(basePrice,
                                                                                    "0.0005"))))

      executor.openTrade(tradeType.stop(shiftedPrice("0.0005"),
                                        "0.0001"),
                         None,
                         expectCall,
                         expectCall)

      // stop should be set to 1.3985, because of delay

      tick(shiftedPrice("0.0002"))

      // after this tick, trade should open by price 1.3992,
      // and stop is set to the 1.3985, because it set from the expected price
      tick(shiftedPrice("0.0008"))

      // and it should not close on price 1.3986
      tick(shiftedPrice("0.0014"))

      // now it should trail stop according to move of actual price
      tick(shiftedPrice("0.0010"))

      // after price 1.3998 stop is expected to be reset to 1.3988
      tick(shiftedPrice("0.0002"))

      // not closed on the price 1.3989
      tick(shiftedPrice("0.0011"))

      assert(trading.balance === initialBalance)

      // and close by stop on price 1.3988
      tick(shiftedPrice("0.0012"))

      assert(trading.balance != initialBalance)
    }

    // now repeat same tests, but for tp

    it should ("set tp for opened trade not closer than configured tp distance for " + tradeType) in new Test {
      override def limits = new IndependentStops(0,
                                                 Fraction("0.001"),
                                                 0,
                                                 0)


      tick(basePrice)

      val executor = trading.newTradeExecutor(new TradeRequest(1000,
                                                               tradeType,
                                                               None))

      executor.openTrade(tradeType.stop(basePrice,
                                        "1"),
                         Some(tradeType.takeProfit(basePrice,
                                                   "0.0002")),
                         expectCall,
                         expectCall)
      // for buy:
      // trade should be opened and should not be closed by tp on price
      // price 1.4002 because tp should be set to 1.401

      tick(shiftedPrice("-0.0002"))

      // if trade not closed - balance not changed
      assert(trading.balance === initialBalance)

      tick(shiftedPrice("-0.001"))

      assert(trading.balance != initialBalance)
    }

    it should ("/-/-/-/ and trail tp closer to the requested value for " + tradeType) in new Test {
      override def limits = new IndependentStops(0,
                                                 Fraction("0.001"),
                                                 0,
                                                 0)

      tick(basePrice)

      val executor = trading.newTradeExecutor(new TradeRequest(1000,
                                                               tradeType,
                                                               None))

      executor.openTrade(tradeType.stop(basePrice,
                                        "1"),
                         Some(tradeType.takeProfit(basePrice,
                                                   "0.0002")),
                         expectCall,
                         expectCall)

      // tp should be set to 1.401, as limited 0.001 from 1.4, after tick
      // it should move down to the 1.4008

      tick(shiftedPrice("0.0002"))

      // after this tick it should not close
      tick(shiftedPrice("-0.0007"))

      // if trade not closed - balance not changed
      assert(trading.balance === initialBalance)

      // after this tick it should  close
      tick(shiftedPrice("-0.0008"))

      assert(trading.balance != initialBalance)
    }

    it should ("/-/-/-/ and trail tp to the requested value for "  + tradeType) in new Test {
      override def limits = new IndependentStops(0,
                                                 Fraction("0.001"),
                                                 0,
                                                 0)

      tick(basePrice)

      val executor = trading.newTradeExecutor(new TradeRequest(1000,
                                                               tradeType,
                                                               None))

      executor.openTrade(tradeType.stop(basePrice,
                                        "1"),
                         Some(tradeType.takeProfit(basePrice,
                                                   "0.0002")),
                         expectCall,
                         expectCall)

      // tp should be set to 1.401, as limited 0.001 from 1.4,
      //
      // after tick it should move down to the 1.4008

      tick(shiftedPrice("0.0002"))
      tick(shiftedPrice("0.0004"))

      // after this tick it should be on requested value 1.4002
      tick(shiftedPrice("0.0012"))

      // after this tick it should not close
      tick(shiftedPrice("-0.0001"))

      // if trade not closed - balance not changed
      assert(trading.balance === initialBalance)

      // after this tick it should  close
      tick(shiftedPrice("-0.0002"))

      assert(trading.balance != initialBalance)
    }

    it should ("count tp distance for limit from expected open price for "  + tradeType) in new Test {
      override def limits = new IndependentStops(0,
                                                 Fraction("0.001"),
                                                 0,
                                                 0)

      tick(basePrice)

      val executor = trading.newTradeExecutor(new TradeRequest(1000,
                                                               tradeType,
                                                               Some(tradeType.delay(basePrice,
                                                                                    "0.0005"))))

      // this requests tp to be 1.3997
      executor.openTrade(tradeType.stop(basePrice,
                                        "1"),

        Some(tradeType.takeProfit(shiftedPrice("0.0005"),
                                  "0.0002")),
                         expectCall,
                         expectCall)

      // tp should be set to 1.4005, because of delay
      tick(shiftedPrice("0.0002"))

      // after this tick, trade should open by price 1.3992,
      // and tp is set to the 1.4002, because it set from the expected price
      tick(shiftedPrice("0.0008"))

      // and it should tno close on price 1.4001
      tick(shiftedPrice("-0.0001"))

      assert(trading.balance === initialBalance)

      // and close by stop on price 1.4002
      tick(shiftedPrice("-0.0002"))

      assert(trading.balance != initialBalance)
    }

    it should ("/-/-/-/-/ for "  + tradeType + " and trail tp after trade opened") in new Test {
      override def limits = new IndependentStops(0,
                                                 Fraction("0.001"),
                                                 0,
                                                 0)

      tick(basePrice)

      val executor = trading.newTradeExecutor(new TradeRequest(1000,
                                                               tradeType,
                                                               Some(tradeType.delay(basePrice,
                                                                                    "0.0005"))))

      // this requests tp to be 1.3997
      executor.openTrade(tradeType.stop(basePrice,
                                        "1"),

        Some(tradeType.takeProfit(shiftedPrice("0.0005"),
                                  "0.0002")),
                         expectCall,
                         expectCall)

      // tp should be set to 1.4005, because of delay
      tick(shiftedPrice("0.0002"))

      // after this tick, trade should open by price 1.3992,
      // and tp is set to the 1.4002, because it set from the expected price
      tick(shiftedPrice("0.0008"))

      // and it should tno close on price 1.4001
      tick(shiftedPrice("-0.0001"))

      assert(trading.balance === initialBalance)

      // and close by stop on price 1.4002
      tick(shiftedPrice("-0.0002"))

      assert(trading.balance != initialBalance)
    }

    it should ("set delay as close as possible for "  + tradeType) in new Test {
      override def limits = new IndependentStops(0,
                                                 0,
                                                 Fraction("0.001"),
                                                 0)

      tick(basePrice)

      // this actually sets delay price to the 1.399 for buy, as delay limited to 0.001
      val executor = trading.newTradeExecutor(new TradeRequest(1000,
                                                               tradeType,
                                                               Some(tradeType.delay(basePrice,
                                                                                    "0.0005"))))
      

      executor.openTrade(tradeType.stop(basePrice,
                                        "1"),
                         None,
                         expectCall,
                         failIfCalled)

      // this should not open trade
      tick(shiftedPrice("0.0005"))
      tick(shiftedPrice("0.0009"))

      assert(trading.margin === Fraction(0))

      // this should open trade
      tick(shiftedPrice("0.001"))

      assert(trading.margin != Fraction(0))
    }

    it should ("/-/-/-/ for "  + tradeType + " and move closer to requested if possible") in new Test {
      override def limits = new IndependentStops(0,
                                                 0,
                                                 Fraction("0.001"),
                                                 0)

      tick(basePrice)

      // this actually sets delay price to the 1.399 for buy, as delay limited to 0.001
      val executor = trading.newTradeExecutor(new TradeRequest(1000,
                                                               tradeType,
                                                               Some(tradeType.delay(basePrice,
                                                                                    "0.0005"))))
      

      executor.openTrade(tradeType.stop(basePrice,
                                        "1"),
                         None,
                         expectCall,
                         failIfCalled)

      // this should not open trade
      tick(shiftedPrice("0.0005"))
      tick(shiftedPrice("0.0009"))

      assert(trading.margin === Fraction(0))

      tick(shiftedPrice("0.0005"))
      tick(shiftedPrice("0.0000"))

      // this will allow delay to shift to 1.3991
      tick(shiftedPrice("-0.0001"))
      
      // this should not open trade
      tick(shiftedPrice("0.0008"))

      assert(trading.margin === Fraction(0))

      // this should open trade
      tick(shiftedPrice("0.0009"))

      assert(trading.margin != Fraction(0))
    }


    it should ("/-/-/-/ for "  + tradeType + " and move to requested (and not further) if possible") in new Test {
      override def limits = new IndependentStops(0,
                                                 0,
                                                 Fraction("0.001"),
                                                 0)

      tick(basePrice)

      // this actually sets delay price to the 1.399 for buy, as delay limited to 0.001
      val executor = trading.newTradeExecutor(new TradeRequest(1000,
                                                               tradeType,
                                                               Some(tradeType.delay(basePrice,
                                                                                    "0.0005"))))
      

      executor.openTrade(tradeType.stop(basePrice,
                                        "1"),
                         None,
                         expectCall,
                         failIfCalled)

      // this should not open trade
      tick(shiftedPrice("0.0005"))
      tick(shiftedPrice("0.0009"))

      assert(trading.margin === Fraction(0))

      tick(shiftedPrice("0.0005"))
      tick(shiftedPrice("0.0000"))

      // this will allow delay to shift to 1.3991
      tick(shiftedPrice("-0.0001"))

      // this will allow delay to shift to 1.3995
      tick(shiftedPrice("-0.0008"))
      
      // this should not open trade
      tick(shiftedPrice("0.0004"))

      assert(trading.margin === Fraction(0))

      // this should open trade
      tick(shiftedPrice("0.0005"))

      assert(trading.margin != Fraction(0))
    }



  }


  testSettingStopAndTpIfLimitedByDistance(Sell)
  testSettingStopAndTpIfLimitedByDistance(Buy)



  it should "correctly process stop crossing current price, but not crossing expected open price:\n  1. trade is delayed \n  2. stop crossed at current price\n  2. stop not crossed on expected open price" in new Test {

    // 0. Start conditions:
    verbose("0. Start conditions")
    // Comission = 0
    comissionFactor = 0
    // Balance_BC = 100
    initialBalance = 100
    // Margin_BC = 0
    assert(trading.margin === Fraction(0))
    // Free Margin_BC = 100
    assert(trading.freeMargin === Fraction(100))
    // Profit = 0
    assert(trading.profit === Fraction(0))
    // Equity_BC = 100
    assert(trading.equity === Fraction(100))
    // Margin_Level_% = N/A
    assert(trading.marginLevel === None)

    // **** Entered on buy trade ****
    // 1. Buy 1000 on price = 1.3000:
    verbose("1. Sell 1000 on price <= 1.3000 (price now = 1.4)")

    tick("1.4")

    val executor = trading.newTradeExecutor(new TradeRequest(1000,
                                                             Buy,
                                                             Some(Boundary <= "1.3")))

    executor.openTrade(Boundary >= "1.35",
                       None,
                       failIfCalled,
                       failIfCalled)
  }

  it should "execute trade and don't close if stop in freeze zone" in new Test {
    // ### Losing sell without comission ###

    override def limits = new IndependentStops(0,
                                               0,
                                               0,
                                               Fraction("0.001"))


    // 0. Start conditions:
    verbose("0. Start conditions")

    // Comission = 0
    comissionFactor = 0
    // Balance_BC = 100
    assert(trading.balance === Fraction(100))
    // Margin_BC = 0
    assert(trading.margin === Fraction(0))
    // Free Margin_BC = 100
    assert(trading.freeMargin === Fraction(100))
    // Profit = 0
    assert(trading.profit === Fraction(0))
    // Equity_BC = 100
    assert(trading.equity === Fraction(100))
    // Margin_Level_% = N/A
    assert(trading.marginLevel === None)

    // **** Entered on sell trade ****
    // 1. Sell 1000 on price = 1.0230:
    verbose("1. Sell 1000 on price = 1.0230")
    tick("1.023")
    
    val executor = trading.newTradeExecutor(new TradeRequest(1000,
                                                             Buy,
                                                             None))

    // stop is 1.0215
    executor.openTrade(Buy.stop("1.023",
                                "0.0015"),
                       None,
                       expectCall,
                       failIfCalled)

    tick("1.0223")

    // in freeze zone, should not close

    executor.closeTrade

    assert(trading.balance === initialBalance)

    // after this tick not in freeze zone, can be changed
    tick("1.0226")

    assert(trading.balance != initialBalance)

  }

  it should "not force move tp after trade opened" in new Test {
    // ### Losing sell without comission ###

    override def limits = new IndependentStops(Fraction("0.003"),
                                               Fraction("0.003"),
                                               Fraction("0.001"),
                                               0)

    // 0. Start conditions:
    verbose("0. Start conditions")
    // Comission = 0
    comissionFactor = 0
    // Balance_BC = 100
    assert(trading.balance === Fraction(100))
    // Margin_BC = 0
    assert(trading.margin === Fraction(0))
    // Free Margin_BC = 100
    assert(trading.freeMargin === Fraction(100))
    // Profit = 0
    assert(trading.profit === Fraction(0))
    // Equity_BC = 100
    assert(trading.equity === Fraction(100))
    // Margin_Level_% = N/A
    assert(trading.marginLevel === None)

    tick("1")

    val executor = trading.newTradeExecutor(new TradeRequest(1000,
                                                             Buy,
                                                             Some(Buy.delay("1", "0.0001"))))

    executor.openTrade(Buy.stop("0.2"),
                       Some(Buy.takeProfit("1.0001")),
                       expectCall,
                       failIfCalled)

    // actual buy price will be 0.999, not 0.9999 because of limit
    assert(executor.delayUntil === Some(Boundary <= Fraction("0.999")))

    // actual tp will be 0.999 + 0.003 = 1.002 because of limits
    assert(executor.takeProfit === Some(Boundary >= Fraction("1.002")))

    tick("0.999")
    // this should make trade opened
    assert(trading.margin != Fraction.ZERO)

    // however, we still have unsatisfied set request for both take profit and delay.
    // And we should not get take profit moved in case if delay is moved

    // this will cause delay to be shifted, but tp should not shift
    tick("1.0001")

    // delay should not be moved if trade is already opened
    assert(executor.delayUntil === Some(Boundary <= Fraction("0.999")))

    // tp should not be changed
    assert(executor.takeProfit === Some(Boundary >= Fraction("1.002")))
  }

  it should "move tp if it satisfied after delay trailed" in new Test {
    // ### Losing sell without comission ###

    override def limits = new IndependentStops(Fraction("0.001"),
                                               Fraction("0.001"),
                                               Fraction("0.001"),
                                               0)

    // 0. Start conditions:
    verbose("0. Start conditions")
    // Comission = 0
    comissionFactor = 0
    // Balance_BC = 100
    assert(trading.balance === Fraction(100))
    // Margin_BC = 0
    assert(trading.margin === Fraction(0))
    // Free Margin_BC = 100
    assert(trading.freeMargin === Fraction(100))
    // Profit = 0
    assert(trading.profit === Fraction(0))
    // Equity_BC = 100
    assert(trading.equity === Fraction(100))
    // Margin_Level_% = N/A
    assert(trading.marginLevel === None)

    tick("1")

    val executor = trading.newTradeExecutor(new TradeRequest(1000,
                                                             Buy,
                                                             Some(Buy.delay("1", "0.0001"))))

    executor.openTrade(Buy.stop("0.2"),
                       Some(Buy.takeProfit("1")),
                       failIfCalled,
                       failIfCalled)

    // actual buy price will be 0.999, not 0.9999 because of limit
    assert(executor.delayUntil === Some(Boundary <= Fraction("0.999")))

    // actual tp will be 0.999 + 0.001 = 1.000 and this is satisfying
    // request
    assert(executor.takeProfit === Some(Boundary >= Fraction("1.000")))

    // trade is still not open
    assert(trading.margin === Fraction.ZERO)

    // this will cause delay to be shifted, and tp _should_ shift
    tick("1.0001")

    // delay shouldbe moved
    assert(executor.delayUntil === Some(Boundary <= Fraction("0.9991")))

    // tp should be changed
    assert(executor.takeProfit === Some(Boundary >= Fraction("1.0001")))
  }

  it should "validate requested stop against requested delay, not actual" in new Test {
    override def limits = new IndependentStops(Fraction("0.0005"),
                                               Fraction("0.0005"),
                                               Fraction("0.0005"),
                                               0)

    tick("1.36702")

    val executor = trading.newTradeExecutor(new TradeRequest(1000,
                                                             Sell,
                                                             Some(Boundary >= "1.36712")))

    executor.openTrade(Boundary >= "1.36722",
                       None,
                       failIfCalled,
                       failIfCalled)

  }

  it should "fire no change events if no trades" in new Test {
    tick("1.36702")
    tick("1.36704")
    tick("1.36705")
  }

  it should "fire equityMayBeChanged event if at least one trade opened and price changed" in new Test {

    val onEquityChange = mock[() => Unit]

    (onEquityChange.apply _).expects()

    (onEquityChange.apply _).expects()

    trading.equityMayBeChanged += onEquityChange
    trading.balanceMayBeChanged += failIfCalled

    tick("1.36702")

    val executor = trading.newTradeExecutor(new TradeRequest(1000,
                                                             Sell,
                                                             Some(Boundary >= "1.36712")))

    executor.openTrade(Boundary >= "1.36722",
                       None,
                       failIfCalled,
                       failIfCalled)

    tick("1.36704")
    tick("1.36705")
  }

  it should "fire balanceMayBeChanged event if trade closed" in new Test {

    val onBalanceChange = mock[() => Unit]

    (onBalanceChange.apply _).expects()

    trading.balanceMayBeChanged += onBalanceChange

    tick("1.36702")

    val executor = trading.newTradeExecutor(new TradeRequest(1000,
                                                             Sell,
                                                             None))

    executor.openTrade(Boundary >= "1.36722",
                       None,
                       expectCall,
                       failIfCalled)

    tick("1.36704")
    tick("1.36705")

    executor.closeTrade
  }

  it should "open non-delayed Buy by ask and close by bid" in new Test {

    val bid = Fraction(1)
    val ask = Fraction(2)

    tickBidAsk(bid, ask)

    val executor = trading.newTradeExecutor(new TradeRequest(100,
                                                             Buy,
                                                             None))
    executor.openTrade(Boundary <= "0.1",
                       None,
                       expectCall,
                       failIfCalled)

    executor.closeTrade()

    val result = executor.result

    assert(result.openPrice === ask)
    assert(result.closePrice === bid)
    assert(result.profit === (bid - ask) * 100)
  }

  it should "open non-delayed Sell by bid and close by ask" in new Test {
    val bid = Fraction(1)
    val ask = Fraction(2)

    tickBidAsk(bid, ask)

    val executor = trading.newTradeExecutor(new TradeRequest(100,
                                                             Sell,
                                                             None))
    executor.openTrade(Boundary >= "10",
                       None,
                       expectCall,
                       failIfCalled)

    executor.closeTrade()

    val result = executor.result

    assert(result.openPrice === bid)
    assert(result.closePrice === ask)
    assert(result.profit === (bid - ask) * 100)
  }

  it should "open delayed Buy when ask crosses boundary (on ask if OpenAndCloseBy.CurrentPrice)" in new Test {
    tickBidAsk("1", "2")

    override def openAndCloseBy = OpenAndCloseBy.CurrentPrice

    val executor = trading.newTradeExecutor(new TradeRequest(100,
                                                             Buy,
                                                             Some(Buy.delay("1.5"))))

    executor.openTrade(Boundary <= "0.1",
                       None,
                       expectCall,
                       failIfCalled)

    assert(executor.isOpened === false)

    tickBidAsk("0.9", "1.8")

    assert(executor.isOpened === false)

    tickBidAsk("1", "1.4")

    assert(executor.isOpened === true)

    assert(executor.status.openPrice === Fraction("1.4"))

  }

  it should "open delayed Buy when ask crosses boundary (on delay price if OpenAndCloseBy.WorstPrice)" in new Test {
    tickBidAsk("1", "2")

    override def openAndCloseBy = OpenAndCloseBy.WorstPrice

    val executor = trading.newTradeExecutor(new TradeRequest(100,
                                                             Buy,
                                                             Some(Buy.delay("1.5"))))

    executor.openTrade(Boundary <= "0.1",
                       None,
                       expectCall,
                       failIfCalled)

    assert(executor.isOpened === false)

    tickBidAsk("0.9", "1.8")

    assert(executor.isOpened === false)

    tickBidAsk("1", "1.4")

    assert(executor.isOpened === true)

    // as worst price selected, delay target price should be used
    assert(executor.status.openPrice === Fraction("1.5"))

  }

  it should "open delayed Sell when bid crosses boundary (on bid if OpenAndCloseBy.CurrentPrice)" in new Test {
    tickBidAsk("1", "2")

    override def openAndCloseBy = OpenAndCloseBy.CurrentPrice

    val executor = trading.newTradeExecutor(new TradeRequest(100,
                                                             Sell,
                                                             Some(Sell.delay("3"))))

    executor.openTrade(Boundary >= "10",
                       None,
                       expectCall,
                       failIfCalled)

    assert(executor.isOpened === false)

    tickBidAsk("2", "2.8")

    assert(executor.isOpened === false)

    tickBidAsk("2.8", "3.2")

    assert(executor.isOpened === false)

    tickBidAsk("3.1", "3.2")

    assert(executor.isOpened === true)

    assert(executor.status.openPrice === Fraction("3.1"))

  }

  it should "open delayed Sell when bid crosses boundary (on delay price if OpenAndCloseBy.WorstPrice)" in new Test {
    tickBidAsk("1", "2")

    override def openAndCloseBy = OpenAndCloseBy.WorstPrice

    val executor = trading.newTradeExecutor(new TradeRequest(100,
                                                             Sell,
                                                             Some(Sell.delay("3"))))

    executor.openTrade(Boundary >= "10",
                       None,
                       expectCall,
                       failIfCalled)

    assert(executor.isOpened === false)

    tickBidAsk("2", "2.9")

    assert(executor.isOpened === false)

    tickBidAsk("2.5", "3.1")

    assert(executor.isOpened === false)

    tickBidAsk("3.2", "3.4")

    assert(executor.isOpened === true)

    // as worst price selected, delay target price should be used
    assert(executor.status.openPrice === Fraction("3"))

  }

  it should "validate Buy stop and tp against bid (stop close case)" in new Test {

    tickBidAsk("2", "3")

    val executor = trading.newTradeExecutor(new TradeRequest(100,
                                                             Buy,
                                                             None))

    // these should be threat as correct
    executor.openTrade(Boundary <= "1.9",
                       Some(Boundary >= "2.1"),
                       expectCall,
                       expectCall)

    assert(executor.isOpened === true)
    assert(executor.isClosed === false)

    tickBidAsk("2.09", "2.09")

    assert(executor.isClosed === false)

    // now it should be called by stop
    tickBidAsk("1.9", "2.3")

    assert(executor.isClosed === true)

    val result = executor.result

    assert(result.openPrice === Fraction("3"))
    assert(result.closePrice === Fraction("1.9"))
    assert(result.profit < 0)
  }

  it should "validate Buy stop and tp against bid (tp close case)" in new Test {

    tickBidAsk("2", "3")

    val executor = trading.newTradeExecutor(new TradeRequest(100,
                                                             Buy,
                                                             None))

    // these should be threat as correct
    executor.openTrade(Boundary <= "1.9",
                       Some(Boundary >= "2.1"),
                       expectCall,
                       expectCall)

    assert(executor.isOpened === true)
    assert(executor.isClosed === false)

    tickBidAsk("2.09", "2.09")

    assert(executor.isClosed === false)

    // now it should be called by tp
    tickBidAsk("2.1", "2.3")

    assert(executor.isClosed === true)

    val result = executor.result

    assert(result.openPrice === Fraction("3"))
    assert(result.closePrice === Fraction("2.1"))
    assert(result.profit < 0)

  }

  it should "validate Sell stop and tp against ask (stop close case)" in new Test {
    initialBalance = 10000
    tickBidAsk("1", "2")

    val executor = trading.newTradeExecutor(new TradeRequest(100,
                                                             Sell,
                                                             None))

    // these should be threat as correct
    executor.openTrade(Boundary >= "2.1",
                       Some(Boundary <= "1.9"),
                       expectCall,
                       expectCall)

    assert(executor.isOpened === true)
    assert(executor.isClosed === false)

    tickBidAsk("2.09", "2.09")

    assert(executor.isClosed === false)

    // now it should be called by stop
    tickBidAsk("2.1", "2.3")

    assert(executor.isClosed === true)

    val result = executor.result

    assert(result.openPrice === Fraction("1"))
    assert(result.closePrice === Fraction("2.3"))
    assert(result.profit < 0)

  }

  it should "validate Sell stop and tp against ask (tp close case)" in new Test {
    initialBalance = 10000
    tickBidAsk("1", "2")

    val executor = trading.newTradeExecutor(new TradeRequest(100,
                                                             Sell,
                                                             None))

    // these should be threat as correct
    executor.openTrade(Boundary >= "2.1",
                       Some(Boundary <= "1.9"),
                       expectCall,
                       expectCall)

    assert(executor.isOpened === true)
    assert(executor.isClosed === false)

    tickBidAsk("2.09", "2.09")

    assert(executor.isClosed === false)

    // now it should be called by tp
    tickBidAsk("1.9", "2.3")

    assert(executor.isClosed === true)

    val result = executor.result

    assert(result.openPrice === Fraction("1"))
    assert(result.closePrice === Fraction("2.3"))
    assert(result.profit < 0)

  }



}
