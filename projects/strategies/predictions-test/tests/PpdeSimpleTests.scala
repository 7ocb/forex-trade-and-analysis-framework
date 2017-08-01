package tests.ppdesimple

import tas.types.{
  Period,
  Time,
  Interval,
  Fraction,
  Trade,
  Sell,
  Buy,
  Boundary,
  Price
}

import tas.types.Fraction.{
  string2Fraction,
  int2Fraction
}

import tas.Bound

import scala.collection.mutable.ListBuffer

import tas.strategies.activeness.ActivenessCondition
import tas.strategies.activeness.AlwaysActive

import org.scalatest.FlatSpec
import org.scalamock.scalatest.MockFactory

import tas.testing.{
  TimerSupport,
  LiteralFloatFormatting
}

import testing.utils.reactions.StrategyBehaviorTests


import tas.events.{
  Event,
  SyncSubscription
}

import tas.output.logger.{Logger, NullLogger, ScreenLogger}

import tas.timers.Timer
import tas.sources.periods.PeriodSource

import tas.ppdesimple.strategy.PpdeSimple

import tas.trading.{
  TradeBackend,
  TradeExecutor,
  TradeRequest
}


import tas.ppdesimple.strategy.{
  PpdeSimple,
  AllowedTrades,
  AllowedOnlyBuys,
  AllowedOnlySells,
  AllowedBoth,
  IfNoDirection,
  OppositeIfNoDirection,
  SameIfNoDirection
}


trait PpdeSimpleBuilder extends StrategyBehaviorTests.StrategyFactory {

  def stopDistance = Fraction("0.001")
  def takeDistance = Fraction("0.001")
  def orderDelayDistance = Fraction("0.001")
  def tradeRiskFactor = Fraction("0.01")
  def comissionFactor = Fraction("0.0003")
  def tradeValueGranularity = 100
  def ifNoDirection:IfNoDirection = SameIfNoDirection
  def periodsToDetectSerie = 1
  def directionDetectionTolerance = Fraction("0.0002")
  def activenessCondition:ActivenessCondition = AlwaysActive
  def allowedTrades:AllowedTrades = AllowedBoth


  def createStrategy(timer:Timer,
                     periodsSourceToUse:PeriodSource,
                     trading:TradeBackend,
                     loggerToUse:Logger) = new PpdeSimple(timer,
                                                          new PpdeSimple.Config(activenessCondition,
                                                                                stopDistance,
                                                                                takeDistance,
                                                                                orderDelayDistance,
                                                                                tradeValueGranularity,
                                                                                ifNoDirection,
                                                                                directionDetectionTolerance,
                                                                                periodsToDetectSerie,
                                                                                tradeRiskFactor,
                                                                                comissionFactor,
                                                                                allowedTrades),
                                                          new PpdeSimple.Context {
                                                            def tradeBackend:TradeBackend  = trading
                                                            def logger:Logger = loggerToUse
                                                            def periodsSource:PeriodSource = periodsSourceToUse
                                                          } )
}

class PpdeSimpleTests extends StrategyBehaviorTests with LiteralFloatFormatting {
  behavior of "strategy: ppde simple"

  def message(msgs:String*) = msgs.mkString("\n    ")

  override def logger = NullLogger

  abstract class Test extends super.Test {
    def eventPeriodEnded(open:Fraction, close:Fraction):Unit = {
      eventPeriodEnded(Price.fromBid(open, 0),
                       Price.fromBid(close, 0))
    }
  }

  it should "do nothing if not active" in
  new Test with PpdeSimpleBuilder {

    override def activenessCondition = new ActivenessCondition {
        def isActive = false
        def changedEvent = new SyncSubscription[()=>Unit]()
      }

    eventPeriodEnded("1.1", "1.3")
    eventPeriodEnded("1.3", "1.3010")
    eventPeriodEnded("1.3", "1.3")
    eventPeriodEnded("1.3", "1.4")
    eventPeriodEnded("1.4", "1.3")
    eventPeriodEnded("1.3", "1.2")
  }

  it should "request buy when one period up if periods to detect serie = 1" in
  new Test with PpdeSimpleBuilder {

    eventPeriodEnded("1.2", "1.5")

    expectBalanceChecked("400")
    expectTradeRequested(Buy,
                         5700,
                         delay = Boundary <= "1.499",
                         stop = Boundary <= "1.498",
                         takeProfit = Boundary >= "1.500")

  }

  it should "don't pass delay boundary if delay offset is zero" in
  new Test with PpdeSimpleBuilder {
    override def orderDelayDistance = Fraction("0")

    info("waiting for issue#160")
    pending

    eventPeriodEnded("1.2", "1.5")

    expectBalanceChecked("400")
    expectTradeRequested(Buy,
                         5700,
                         delay = Boundary <= "1.499",
                         stop = Boundary <= "1.498",
                         takeProfit = Boundary >= "1.500")
  }

  it should "request buy when two period up if periods to detect serie = 2" in
  new Test with PpdeSimpleBuilder {

    override def periodsToDetectSerie = 2

    eventPeriodEnded("1.2", "1.35")
    eventPeriodEnded("1.2", "1.5")

    expectBalanceChecked("400")
    expectTradeRequested(Buy,
                         5700,
                         delay = Boundary <= "1.499",
                         stop = Boundary <= "1.498",
                         takeProfit = Boundary >= "1.500")

  }

  it should message("request nothing when two period have different direction",
                    "and periods to detect serie = 2") in
  new Test with PpdeSimpleBuilder {

    override def periodsToDetectSerie = 2

    eventPeriodEnded("1.2", "1.35")
    eventPeriodEnded("1.35", "1.2")

  }

  it should message("request buy when up and nodirection if periods to detect serie = 2",
                    "and ifNoDirection = same") in
  new Test with PpdeSimpleBuilder {

    override def periodsToDetectSerie = 2

    eventPeriodEnded("1.2", "1.5")
    eventPeriodEnded("1.5", "1.5")

    expectBalanceChecked("400")
    expectTradeRequested(Buy,
                         5700,
                         delay = Boundary <= "1.499",
                         stop = Boundary <= "1.498",
                         takeProfit = Boundary >= "1.500")

  }

  it should message("request nothing when up and nodirection if periods to detect serie = 2",
                    "and ifNoDirection = opposite") in
  new Test with PpdeSimpleBuilder {

    override def periodsToDetectSerie = 2
    override def ifNoDirection = OppositeIfNoDirection

    eventPeriodEnded("1.2", "1.35")
    eventPeriodEnded("1.35", "1.35")

  }

  it should message("close previous and open next on next period, up-up -> buy-buy",
                    "if first trade was not opened") in
  new Test with PpdeSimpleBuilder {

    eventPeriodEnded("1.2", "1.5")

    expectBalanceChecked("400")
    val firstTrade = expectTradeRequested(Buy,
                                          5700,
                                          delay = Boundary <= "1.499",
                                          stop = Boundary <= "1.498",
                                          takeProfit = Boundary >= "1.500")

    eventPeriodEnded("1.5", "1.6")

    firstTrade.expectClosed()

    expectBalanceChecked("400")
    val secondTrade = expectTradeRequested(Buy,
                                           5700,
                                           delay = Boundary <= "1.599",
                                           stop = Boundary <= "1.598",
                                           takeProfit = Boundary >= "1.600")

  }

  it should message("close previous and open next on next period, down-down -> sell-sell",
                    "if first trade was not opened") in
  new Test with PpdeSimpleBuilder {

    eventPeriodEnded("1.5", "1.2")

    expectBalanceChecked("400")
    val firstTrade = expectTradeRequested(Sell,
                                          5700,
                                          delay = Boundary >= "1.201",
                                          stop = Boundary >= "1.202",
                                          takeProfit = Boundary <= "1.200")

    eventPeriodEnded("1.2", "1.1")

    firstTrade.expectClosed()

    expectBalanceChecked("400")
    val secondTrade = expectTradeRequested(Sell,
                                           5700,
                                           delay = Boundary >= "1.101",
                                           stop = Boundary >= "1.102",
                                           takeProfit = Boundary <= "1.100")

  }


  it should message("do nothing if up period too small") in
  new Test with PpdeSimpleBuilder {

    override def directionDetectionTolerance = "0.0001"

    eventPeriodEnded("1.2", "1.2001")

  }

  it should message("do nothing if down period too small") in
  new Test with PpdeSimpleBuilder {

    override def directionDetectionTolerance = "0.0001"

    eventPeriodEnded("1.2004", "1.2003")

  }

  it should message("do nothing if no direction and up period too small") in
  new Test with PpdeSimpleBuilder {

    override def directionDetectionTolerance = "0.0001"

    eventPeriodEnded("1.2", "1.2")
    eventPeriodEnded("1.2", "1.2001")

  }

  it should message("open buy if no direction, and two too small ups") in
  new Test with PpdeSimpleBuilder {

    // this test is according to
    //
    // Если: abs(цена открытия периода - цена закрытия периода) <=
    // directionDetectionTolerance - то период считается не имеющим
    // направления и его направление принимается согласно параметру
    // "ifNoDirection" относительно направления прошлого периода.

    override def directionDetectionTolerance = "0.0001"

    eventPeriodEnded("1.2", "1.2")
    eventPeriodEnded("1.2", "1.2001")
    eventPeriodEnded("1.2001", "1.2002")

    expectBalanceChecked("400")
    expectTradeRequested(Buy,
                         5700,
                         delay = Boundary <= "1.1992",
                         stop = Boundary <= "1.1982",
                         takeProfit = Boundary >= "1.2002")
  }

  it should message("do nothing if no direction and down period too small") in
  new Test with PpdeSimpleBuilder {

    // this test is according to
    //
    // Если: abs(цена открытия периода - цена закрытия периода) <=
    // directionDetectionTolerance - то период считается не имеющим
    // направления и его направление принимается согласно параметру
    // "ifNoDirection" относительно направления прошлого периода.

    override def directionDetectionTolerance = "0.0001"
    override def stopDistance = "0.002"
    override def takeDistance = "0.002"

    eventPeriodEnded("1.2004", "1.2004")
    eventPeriodEnded("1.2004", "1.2003")
    eventPeriodEnded("1.2003", "1.2002")

    expectBalanceChecked("400")
    expectTradeRequested(Sell,
                         2300,
                         delay = Boundary >= "1.2012",
                         stop = Boundary >= "1.2032",
                         takeProfit = Boundary <= "1.1992")

  }


  it should message("request buy on up and next buy on no direction",
                    "if ifNoDirection = same") in
  new Test with PpdeSimpleBuilder {

    eventPeriodEnded("1.2", "1.5")

    expectBalanceChecked("400")
    val first = expectTradeRequested(Buy,
                                     5700,
                                     delay = Boundary <= "1.499",
                                     stop = Boundary <= "1.498",
                                     takeProfit = Boundary >= "1.500")

    eventPeriodEnded("1.5", "1.5")
    first.expectClosed()

    expectBalanceChecked("400")
    expectTradeRequested(Buy,
                         5700,
                         delay = Boundary <= "1.499",
                         stop = Boundary <= "1.498",
                         takeProfit = Boundary >= "1.500")
  }

  it should message("request buy on up and then sell on no direction",
                    "if ifNoDirection = opposite") in
  new Test with PpdeSimpleBuilder {

    override def ifNoDirection = OppositeIfNoDirection

    eventPeriodEnded("1.2", "1.5")

    expectBalanceChecked("400")
    val first = expectTradeRequested(Buy,
                                     5700,
                                     delay = Boundary <= "1.499",
                                     stop = Boundary <= "1.498",
                                     takeProfit = Boundary >= "1.500")

    eventPeriodEnded("1.5", "1.5")
    first.expectClosed()

    expectBalanceChecked("400")
    expectTradeRequested(Sell,
                         5700,
                         delay = Boundary >= "1.501",
                         stop = Boundary >= "1.502",
                         takeProfit = Boundary <= "1.500")
  }

}
