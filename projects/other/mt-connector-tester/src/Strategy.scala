package tas.ppdesimple.strategy

import tas.{ActiveValue, NotBound, Bound}

import tas.sources.periods.PeriodSource
import tas.types.{
  Period,
  Time,
  Interval,
  Fraction,
  Trade, Sell, Buy,
  Boundary,
  Price
}

import tas.sources.PeriodDirection
import tas.sources.PeriodDirection.{Direction, Up, Down, NoDirection}

import tas.timers.Timer

import tas.trading.{TradeBackend, TradeRequest, TradeValue,
  TradeHandle, CloseCondition,
  TradeExecutor}

import tas.output.logger.Logger

import tas.events.{
  Subscription,
  SyncSubscription
}

import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer

import tas.strategies.activeness.ActivenessCondition

object PpdeSimpleModified {

  case class Stops(stopDistance:Fraction,
                   takeDistance:Fraction,
                   delayDistance:Fraction)

  case class Config(activeness:ActivenessCondition,
                    tradeValueGranularity:Int,
                    ifNoDirection:IfNoDirection,
                    directionDetectionTolerance:Fraction,
                    periodsCountToDetectSerie:Int,
                    oneTradeRiskFactor:Fraction,
                    comissionFactor:Fraction,
                    allowedTrades:AllowedTrades)

  trait Context {
    def tradeBackend:TradeBackend
    def logger:Logger
    def periodsSource:PeriodSource
  }

  def nextPeriodStartTime(initialTime:Time,
                          period:Interval,
                          shift:Interval) = initialTime.nextPeriodStartTime(period, shift)
}

import PpdeSimpleModified._

class PpdeSimpleModified(timer:Timer,
                         config:Config,
                         var stopsConfig:Stops,
                         context:Context) {

  private val _executors = new ListBuffer[TradeExecutor with Bound]

  private var _previousCompletedPeriod:Period = null
  private var _lastCompletedPeriod:Period = null

  private val serieTracker:SerieSearcher = new SerieSearcher(config.periodsCountToDetectSerie)
  
  context.periodsSource.periodCompleted += onCompletedPeriod

  config.activeness.changedEvent += onActivenessChange

  private def onActivenessChange() = {
    if ( ! config.activeness.isActive) {
      closeAll()
    }
  }

  private def onCompletedPeriod(period:Period):Unit = {
    closeAll()

    if ( ! config.activeness.isActive) return

    _previousCompletedPeriod = _lastCompletedPeriod
    _lastCompletedPeriod = period

    val direction = directionOf(period)
    PeriodDirection.log(context.logger, period, direction)

    serieTracker.onPeriodEnded(direction)

    val serieDirection = serieTracker.serieDirection
    if (serieDirection != NoDirection) {
      openTrade(serieDirection)
    }
  }

  private def openTrade(direction:Direction):Unit = {
    val trade = tradeFor(direction)

    if ( ! config.allowedTrades.isAllowed(trade)) return

    val basePrice = _lastCompletedPeriod.priceClose

    val stopDist = stopsConfig.stopDistance
    val takeProfitDist = stopsConfig.takeDistance
    val delayDist = stopsConfig.delayDistance

    val value = valNumeratorFromStopDistance(stopDist)

    val expectedOpenPrice = trade.expectedOpenPrice(basePrice,
                                                    delayDist)

    val delayBoundary = if (delayDist == Fraction.ZERO) None
                        else Some(trade.delay(basePrice, delayDist))

    val stopBoundary  = trade.stop(expectedOpenPrice,
                                   stopDist)

    val tpBoundary    = trade.takeProfit(expectedOpenPrice,
                                         takeProfitDist)


    val executor = context.tradeBackend.newTradeExecutor(new TradeRequest(value,
                                                                          trade,
                                                                          delayBoundary))

    executor.openTrade(stopBoundary,
                       Some(tpBoundary),
                       () => { /* nothing to do if opened */ },
                       () => { /* nothing to do if closed */ })


    _executors += executor
  }

  private def closeAll() {
    _executors.foreach(e => {
                         e.closeTrade
                         e.unbindAll
                       } )
    _executors.clear()
  }

  private def valNumeratorFromStopDistance(stopDist:Fraction):Int = {
    val riskDenominator = context.tradeBackend.balance * config.oneTradeRiskFactor

    val valNumeratorRaw = riskDenominator / (stopDist - config.comissionFactor)

    (valNumeratorRaw.floorToInt / config.tradeValueGranularity) * config.tradeValueGranularity
  }

  private def tradeFor(direction:Direction) = direction match {
      case Up => Buy
      case Down => Sell
      case NoDirection => throw new RuntimeException("Should not happen!")
    }

  def directionOf(period:Period):Direction = {

    val directionOfIt = PeriodDirection.directionOf(period)

    val periodChange = period.change(_.bid)

    val tooSmallToDetectDirection = periodChange.abs <= config.directionDetectionTolerance

    if (tooSmallToDetectDirection) {

      if (_previousCompletedPeriod != null) {

        val previousPeriodDirection = PeriodDirection.directionOf(_previousCompletedPeriod)

        if (config.ifNoDirection == OppositeIfNoDirection) previousPeriodDirection.opposite
        else previousPeriodDirection

      } else NoDirection

    } else directionOfIt

  }
}
