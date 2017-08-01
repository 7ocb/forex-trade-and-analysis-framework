
package tests.previousdaydirection

import tas.types.{Period, Time, Interval, Fraction}

import tas.types.Fraction.string2Fraction
import tas.types.Fraction.int2Fraction

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

import testing.utils.ReactionToEventTests


import tas.events.{
  Event,
  SyncSubscription
}

import tas.output.logger.{Logger, NullLogger, ScreenLogger}

import tas.timers.Timer
import tas.sources.periods.PeriodSource

import tas.previousdaydirection.strategy.Strategy

import tas.trading.{
  TradeType,
  Sell,
  Buy,
  Boundary,
  TradeBackend,
  TradeExecutor,
  TradeRequest
}


import tas.previousdaydirection.strategy.{
  Strategy,
  AllowedTradeTypes,
  AllowedOnlyBuys,
  AllowedOnlySells,
  AllowedBoth
}

import testing.utils.StrategyBehaviorTests

trait StrategyBuilder extends StrategyBehaviorTests.StrategyFactory {
  def delay = Fraction("0.0020")
  def takeProfitFactor = Fraction(1)
  def firstStopDist = Fraction("0.0040")
  def dontOpenTradeIfStopLessThan = Fraction("0.0013")
  def stopDistanceUpperLimit = Fraction("0.01")
  def periodsToDetectSerie = 1
  def oneTradeRiskBalanceFactor = Fraction("0.01")
  def maxTradesInSerie = 2
  def comissionFactor = Fraction("0.0003")
  def tradeValueGranularity = 100
  def directionTolerance = Fraction("0.0002")
  def activenessCondition:ActivenessCondition = AlwaysActive
  def allowedTradeTypes:AllowedTradeTypes = AllowedBoth


  def createStrategy(timer:Timer,
                     periodsSourceToUse:PeriodSource,
                     trading:TradeBackend,
                     loggerToUse:Logger) = new Strategy(timer,
                                                        new Strategy.Config(activenessCondition,
                                                                            delay,
                                                                            takeProfitFactor,
                                                                            new Strategy.StopsSettings(firstStopDist,
                                                                                                       dontOpenTradeIfStopLessThan,
                                                                                                       stopDistanceUpperLimit),
                                                                            periodsToDetectSerie,
                                                                            oneTradeRiskBalanceFactor,
                                                                            maxTradesInSerie,
                                                                            comissionFactor,
                                                                            tradeValueGranularity,
                                                                            directionTolerance,
                                                                            allowedTradeTypes),
                                                        new Strategy.Context {
                                                          def tradeBackend:TradeBackend  = trading
                                                          def logger:Logger = loggerToUse
                                                          def periodsSource:PeriodSource = periodsSourceToUse
                                                        } )
}

class StrategyTests extends StrategyBehaviorTests with LiteralFloatFormatting {
  behavior of "strategy: ppde"

  override def logger = NullLogger

  // val defaultCreateStrategy = new StrategyBuilder()
  
  it should "request buy when one period up and close it when no direction" in
  new Test with StrategyBuilder {

    eventPeriodEnded("1.2", "1.5")
    expectBalanceChecked("400")
    val firstTrade = expectTradeRequested(Buy, 1000, Boundary <= "1.498", Boundary <= "1.494", Boundary >= "1.502")

    eventPeriodEnded("1.5", "1.5")
    firstTrade.expectClosed()
  }

  it should "request with different value if different balance" in
  new Test with StrategyBuilder {

    eventPeriodEnded("1.2", "1.5")
    expectBalanceChecked("200")
    val firstTrade = expectTradeRequested(Buy, 500, Boundary <= "1.498", Boundary <= "1.494", Boundary >= "1.502")


    eventPeriodEnded("1.5", "1.5")
    firstTrade.expectClosed()
  }

  it should "request with different delay and stop" in
  new Test with StrategyBuilder {
    override def delay = "0.004"
    override def firstStopDist = "0.008"

    eventPeriodEnded("1.2", "1.5")

    expectBalanceChecked("400")
    val firstTrade = expectTradeRequested(Buy, 500, Boundary <= "1.496", Boundary <= "1.488", Boundary >= "1.504")

    eventPeriodEnded("1.5", "1.5")
    firstTrade.expectClosed()
  }

  it should "open trade only after 2nd period in direction if serie = 2" in
  new Test with StrategyBuilder {
    override def periodsToDetectSerie = 2

    eventPeriodEnded("1.1", "1.3")
    eventPeriodEnded("1.3", "1.5")

    expectBalanceChecked("400")
    val firstTrade = expectTradeRequested(Buy, 1000, Boundary <= "1.498", Boundary <= "1.494", Boundary >= "1.502")


    eventPeriodEnded("1.5", "1.5")
    firstTrade.expectClosed()
  }

  it should "open trade on Up, close it and open new on Down if serie = 1" in
  new Test with StrategyBuilder {

    eventPeriodEnded("1.1", "1.3")
    expectBalanceChecked("400")
    val firstTrade = expectTradeRequested(Buy, 1000, Boundary <= "1.298", Boundary <= "1.294", Boundary >= "1.302")

    eventPeriodEnded("1.3", "1.297")
    firstTrade.expectClosed()

    expectBalanceChecked("400")
    val secondTrade = expectTradeRequested(Sell, 1000, Boundary >= "1.299", Boundary >= "1.303", Boundary <= "1.295")
    
    eventPeriodEnded("1.297", "1.297")
    secondTrade.expectClosed()
  }


  
  it should "open trade on Up, and second on next Up if serie = 1" in
  new Test with StrategyBuilder {

    override def takeProfitFactor = Fraction("2")

    eventPeriodEnded("1.1", "1.3")
    expectBalanceChecked("400")
    val firstTrade = expectTradeRequested(Buy, 1000, Boundary <= "1.298", Boundary <= "1.294", Boundary >= "1.306")
    firstTrade.eventOpened()
    eventPeriodEnded("1.3", "1.304")

    expectBalanceChecked("400")
    val secondTrade = expectTradeRequested(Buy, 2300, Boundary <= "1.302", Boundary <= "1.3", Boundary >= "1.306")

    // to calculated to Boundary >= "1.306", which is no change, so there only stop will be updated
    firstTrade.expectStopUpdated(Boundary <= "1.3")
    eventPeriodEnded("1.297", "1.297")

    secondTrade.expectClosed()
    firstTrade.expectClosed()
  }


  it should "dont open buys if limited to sells only" in
  new Test with StrategyBuilder {
    override def allowedTradeTypes = AllowedOnlySells

    eventPeriodEnded("1.1", "1.3")
    eventPeriodEnded("1.3", "1.304")
    eventPeriodEnded("1.297", "1.297")
  }

  it should "dont open sells if limited to buys only" in
  new Test with StrategyBuilder {
    override def allowedTradeTypes = AllowedOnlyBuys

    eventPeriodEnded("1.3", "1.1")
    eventPeriodEnded("1.1", "1.05")
    eventPeriodEnded("1.05", "1")
  }

  it should ("open trade on Up, and no second on next Up if serie = 1 and max trades = 1"
               + "\n but it still must change boundaries for open trades") in
  new Test with StrategyBuilder {
    override def maxTradesInSerie = 1

    eventPeriodEnded("1.4", "1.3")
    expectBalanceChecked("400")
    val firstTrade = expectTradeRequested(Sell, 1000, Boundary >= "1.302", Boundary >= "1.306", Boundary <= "1.298")
    firstTrade.eventOpened()
    eventPeriodEnded("1.303", "1.300")

    firstTrade.expectBoundariesUpdated(Boundary >= "1.303", Boundary <= "1.301")
    eventPeriodEnded("1.297", "1.297")

    firstTrade.expectClosed()
  }

  it should "open no trade if serie = 2 and direction changes constantly" in
  new Test with StrategyBuilder {
    override def periodsToDetectSerie = 2

    eventPeriodEnded("1.4", "1.3")
    eventPeriodEnded("1.3", "1.4")
    eventPeriodEnded("1.4", "1.2")
    eventPeriodEnded("1.2", "1.5")
  }

  
  it should "close trade if it set but was not opened even if in serie" in
  new Test with StrategyBuilder {

    eventPeriodEnded("1.1", "1.3")
    expectBalanceChecked("400")
    val firstTrade = expectTradeRequested(Buy, 1000, Boundary <= "1.298", Boundary <= "1.294", Boundary >= "1.302")

    eventPeriodEnded("1.3", "1.304")
    firstTrade.expectClosed()

    expectBalanceChecked("400")
    val secondTrade = expectTradeRequested(Buy, 2300, Boundary <= "1.302", Boundary <= "1.3", Boundary >= "1.304")
    eventPeriodEnded("1.297", "1.297")

    secondTrade.expectClosed()
  }

  it should "close trade and restart serie if another trade was closed externally" in
  new Test with StrategyBuilder {

    override def periodsToDetectSerie = 2
    override def maxTradesInSerie = 2

    eventPeriodEnded("1.0", "1.1")
    eventPeriodEnded("1.1", "1.3")
    expectBalanceChecked("400")
    val firstTrade = expectTradeRequested(Buy, 1000, Boundary <= "1.298", Boundary <= "1.294", Boundary >= "1.302")


    firstTrade.eventOpened()
    eventPeriodEnded("1.3", "1.304")

    expectBalanceChecked("400")
    val secondTrade = expectTradeRequested(Buy, 2300, Boundary <= "1.302", Boundary <= "1.300", Boundary >= "1.304")
    firstTrade.expectBoundariesUpdated(Boundary <= "1.300", Boundary >= "1.304")
    
    secondTrade.eventOpened()
    eventPeriodEnded("1.303", "1.308")

    expectBoundariesUpdated(List(firstTrade, secondTrade),
                            Boundary <= "1.303", Boundary >= "1.309")
    
    eventPeriodEnded("1.308", "1.35")

    expectBoundariesUpdated(List(firstTrade, secondTrade),
                            Boundary <= "1.308", Boundary >= "1.388")
    
    firstTrade.eventClosedExternally()

    secondTrade.expectClosed()

    eventPeriodEnded("1.35", "1.36")
    eventPeriodEnded("1.36", "1.38")

    expectBalanceChecked("400")
    val thirdTrade = expectTradeRequested(Buy, 1000, Boundary <= "1.378", Boundary <= "1.374", Boundary >= "1.382")
  }

  it should "correctly calculate first and subsequent sells" in
  new Test with StrategyBuilder {
    eventPeriodEnded("1.3", "1.1")
    expectBalanceChecked("400")
    val firstTrade = expectTradeRequested(Sell, 1000, Boundary >= "1.102", Boundary >= "1.106", Boundary <= "1.098")

    eventPeriodEnded("1.1", "1.09")
    firstTrade.expectClosed()

    expectBalanceChecked("400")
    val secondTrade = expectTradeRequested(Sell, 500, Boundary >= "1.092", Boundary >= "1.1" , Boundary <= "1.084")
    eventPeriodEnded("1.09", "1.09")

    secondTrade.expectClosed()
  }

  it should "open Buy if Up, NoDirection" in
  new Test with StrategyBuilder {
    override def periodsToDetectSerie = 2

    eventPeriodEnded("1.0", "1.1")
    eventPeriodEnded("1.1", "1.1")

    expectBalanceChecked("400")
    expectTradeRequested(Buy, 1000, Boundary <= "1.098", Boundary <= "1.094", Boundary >= "1.102")

  }

  it should "open Buy if Up, NoDirection with different first stop and delay" in
  new Test with StrategyBuilder {

    override def periodsToDetectSerie = 2
    override def firstStopDist = "0.008"
    override def delay = "0.003"

    eventPeriodEnded("1.0", "1.1")
    eventPeriodEnded("1.1", "1.1")

    expectBalanceChecked("400")
    expectTradeRequested(Buy, 500, Boundary <= "1.097", Boundary <= "1.089", Boundary >= "1.105")

  }

  it should "not request second trade in serie if period abs(open-close) < delay" in
  new Test with StrategyBuilder {

    override def firstStopDist = "0.008"

    eventPeriodEnded("1.0", "1.1")
    expectBalanceChecked("400")
    val firstTrade = expectTradeRequested(Buy, 500,
                                          delay = Boundary <= "1.098",
                                          stop = Boundary <= "1.09",
                                          takeProfit = Boundary >= "1.106")
    eventPeriodEnded("1.1", "1.1019")
    
    firstTrade.expectClosed()
    eventPeriodEnded("1.1019", "1.1038")
  }

  it should "not open trade if abs(open-close) < directionDetectionTolerance" in
  new Test with StrategyBuilder {

    override def directionTolerance = Fraction("0.003")

    eventPeriodEnded("1.0", "1.002")
    eventPeriodEnded("1.002", "1.004")
    eventPeriodEnded("1.004", "1.005")
  }


  it should "end serie if period abs(open-close) < directionDetectionTolerance" in
  new Test with StrategyBuilder {

    override def firstStopDist = "0.008"
    override def directionTolerance = Fraction("0.04")

    eventPeriodEnded("1.0", "1.1")
    expectBalanceChecked("400")
    val firstTrade = expectTradeRequested(Buy, 500,
                                          delay = Boundary <= "1.098",
                                          stop = Boundary <= "1.09",
                                          takeProfit = Boundary >= "1.106")
    eventPeriodEnded("1.1", "1.13")
    
    firstTrade.expectClosed()
    eventPeriodEnded("1.1019", "1.1038")
  }

  it should "limit maximum stop to the stopDistanceUpperLimit" in
  new Test with StrategyBuilder {
    override def firstStopDist = "0.0200"

    pending // waiting for issue#72
    eventPeriodEnded("1.0", "1.3")

    info("for first in serie")
    expectBalanceChecked("400")
    val firstTrade = expectTradeRequested(Buy,
                                          400,
                                          Boundary <= "1.298",
                                          // note, that this values calculates from stop
                                          // = 0.01, not 0.02
                                          Boundary <= "1.288",
                                          Boundary >= "1.308")

    firstTrade.eventOpened()
    info("for second in serie")
    eventPeriodEnded("1.3", "1.6")

    expectBalanceChecked("400")
    expectTradeRequested(Buy,
                         400,
                         Boundary <= "1.598",
                         Boundary <= "1.588",
                         Boundary >= "1.508")

    expectBoundariesUpdated(List(firstTrade),
                            Boundary <= "1.588",
                            Boundary >= "1.508")

  }

  it should "not calculate value to 0" in
  new Test with StrategyBuilder {

    override def firstStopDist = "0.0200"
    override def tradeValueGranularity = 1000

    pending // waiting for issue#72
    eventPeriodEnded("1.0", "1.3")

    info("for first in serie")
    expectBalanceChecked("400")
    val firstTrade = expectTradeRequested(Buy,
                                          0, // TODO: there must be non null!
                                          Boundary <= "1.298",
                                          // note, that this values calculates from stop
                                          // = 0.01, not 0.02
                                          Boundary <= "1.288",
                                          Boundary >= "1.308")
  }

  behavior of "suppressing trades if stop distance < dontOpenTradeIfStopLessThan"


  it should "don't open first and open second" in
  new Test with StrategyBuilder {

    override def firstStopDist = "0.0008"

    eventPeriodEnded("1.0", "1.3")
    eventPeriodEnded("1.3", "1.304")

    expectBalanceChecked("400")
    expectTradeRequested(Buy, 2300, Boundary <= "1.302", Boundary <= "1.300", Boundary >= "1.304")

  }

  it should "open first and don't open second, but change stops" in
  new Test with StrategyBuilder {
    override def delay = Fraction("0.0005")
    override def dontOpenTradeIfStopLessThan = Fraction("0.0035")

    eventPeriodEnded("1.1", "1.3")
    expectBalanceChecked("400")
    val firstTrade = expectTradeRequested(Buy, 1000,
                                          Boundary <= "1.2995",
                                          Boundary <= "1.2955",
                                          Boundary >= "1.3035")

    firstTrade.eventOpened()
    eventPeriodEnded("1.3", "1.3010")

    expectBoundariesUpdated(List(firstTrade),
                            Boundary <= "1.3",
                            Boundary >= "1.301")

    eventPeriodEnded("1.3", "1.3")
    firstTrade.expectClosed()
  }

  it should "do nothing if not active" in
  new Test with StrategyBuilder {

    override def activenessCondition = new ActivenessCondition {
        def isActive = false
        def changedEvent = new SyncSubscription[()=>Unit]()
      }

    eventPeriodEnded("1.1", "1.3")
    eventPeriodEnded("1.3", "1.3010")
    eventPeriodEnded("1.3", "1.3")
  }

}
