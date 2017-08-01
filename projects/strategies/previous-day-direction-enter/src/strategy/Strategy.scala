package tas.previousdaydirection.strategy

import tas.{ActiveValue, NotBound, Bound}

import tas.sources.periods.PeriodSource
import tas.types.{
  Period,
  Time,
  Interval,
  Fraction,
  Trade, Sell, Buy
}

import tas.sources.PeriodDirection
import tas.sources.PeriodDirection.{Direction, Up, Down, NoDirection}

import tas.timers.Timer

import tas.trading.{TradeBackend, TradeRequest, TradeValue,
                    TradeHandle, CloseCondition,
                    TradeExecutor, Boundary}

import tas.output.logger.Logger

import tas.events.{
  Subscription,
  SyncSubscription
}

import tas.strategies.activeness.ActivenessCondition

import scala.annotation.tailrec

object Strategy {

  case class StopsSettings(firstStopDist:Fraction,
                           dontOpenTradeIfStopLessThan:Fraction,
                           stopDistanceUpperLimit:Fraction)

  case class Config(activeness:ActivenessCondition,
                    delay:Fraction,
                    takeProfitFactor:Fraction,
                    stops:StopsSettings,
                    periodsToDetectSerie:Int,
                    oneTradeRiskBalanceFactor:Fraction,
                    maxTradesInSerie:Int,
                    comissionFactor:Fraction,
                    tradeValueGranularity:Int,
                    directionTolerance:Fraction,
                    allowedTrades:AllowedTradeTypes) {

    def isStopValidForOpeningTrade(stopDistanceToCheck:Fraction) =
      stopDistanceToCheck >= stops.dontOpenTradeIfStopLessThan

    def stopDistance(rawStopDistance:Fraction) =
      rawStopDistance.min(stops.stopDistanceUpperLimit)

    def stopForFirstTradeInSerie = stopDistance(stops.firstStopDist)
  }

  trait Context {
    def tradeBackend:TradeBackend
    def logger:Logger
    def periodsSource:PeriodSource
  }

  def nextPeriodStartTime(initialTime:Time,
                          period:Interval,
                          shift:Interval) = initialTime.nextPeriodStartTime(period, shift)

  def directionOf(period:Period, directionDetectionTolerance:Fraction):Direction = {
    if ((period.priceOpen - period.priceClose).abs <= directionDetectionTolerance) NoDirection
    else PeriodDirection.directionOf(period)
  }
} 

import Strategy._

class Strategy(timer:Timer, config:Config, context:Context) {

  private val _tradeSet = new TradeSet(timer, forceRestartSerieOnExternallyClosed)

  private var _lastCompletedPeriod:Period = null

  private var _serieDirection:Direction = null
  
  private var _serieTracker:SerieSearcher = null  
  private def serieTracker:SerieSearcher = {
    if (_serieTracker == null || _serieTracker.isStopped) {
      _serieTracker = new SerieSearcher(config.periodsToDetectSerie,
                                        onSerieFound)
    }

    _serieTracker
  }
  
  context.periodsSource.periodCompleted += onCompletedPeriod

  config.activeness.changedEvent += onActivenessChange

  private def onActivenessChange() = {
    if ( ! config.activeness.isActive) {
      logSerieEnd("closing by end of week")

      closeSerie()
    }
  }

  private def onCompletedPeriod(period:Period):Unit = {

    if ( ! config.activeness.isActive) return

    _lastCompletedPeriod = period
    _tradeSet.closePending()

    val direction = directionOf(period)
    PeriodDirection.log(context.logger, period, direction)

    val inSerie = _serieDirection != null
    if (inSerie) {
      if (_serieDirection != direction) {
        onDirectionChanged()
      } else {
        continueSerie()
      } 
    } else {
      serieTracker.onPeriodEnded(direction)
    } 
  }

  private def onSerieFound(direction:Direction) = {
    _serieDirection = direction
    openSerie()
  } 

  private def closeSerie() {
    _serieTracker = null
    _serieDirection = null
    _tradeSet.closeAll()
  } 

  private def logSerieEnd(reason:String) = {
    context.logger.log("Serie ended (", reason, ")")
  }

  private def onDirectionChanged() = {
    logSerieEnd("direction changed")

    closeSerie()

    // start tracking new serie
    serieTracker.onPeriodEnded(directionOf(_lastCompletedPeriod))
  }

  private def forceRestartSerieOnExternallyClosed:Unit = {
    logSerieEnd("stop/tp close")

    closeSerie()

    // don't put this period to new serie tracker, as force stopping occurs
    // during the period, not at the end
  }

  private def openSerie():Unit = {
    val tradeType = tradeTypeFor(_serieDirection)

    if ( ! config.allowedTrades.isAllowed(tradeType)) return

    val basePrice = _lastCompletedPeriod.priceClose
    context.logger.log("#### Opening new serie ####")

    val stopDist = config.stopForFirstTradeInSerie
    val takeProfitDist = config.takeProfitFactor * stopDist

    val delayOffset = config.delay

    val expectedOpenPrice = tradeType.expectedOpenPrice(basePrice,
                                                        delayOffset)

    val delayBoundary = tradeType.delay(basePrice,
                                        delayOffset)

    val stopBoundary  = tradeType.stop(expectedOpenPrice,
                                       stopDist)

    val tpBoundary    = tradeType.takeProfit(expectedOpenPrice,
                                             takeProfitDist)

    if ( ! isStopValidForOpeningTrade(stopDist) ) return

    val valNumerator = valNumeratorFromStopDistance(stopDist)

    val executor = context.tradeBackend.newTradeExecutor(new TradeRequest(valNumerator,
                                                                          tradeType,
                                                                          Some(delayBoundary)))

    _tradeSet.setBoundaries(stopBoundary, tpBoundary)
    
    _tradeSet.open(executor)
  }
  
  private def continueSerie():Unit = {
    val tradeType = tradeTypeFor(_serieDirection)

    if ( ! config.allowedTrades.isAllowed(tradeType)) return

    val basePrice = _lastCompletedPeriod.priceClose
    context.logger.log("=== Continuing serie")

    val stopPosition = _lastCompletedPeriod.priceOpen

    val delayOffset = config.delay

    val expectedOpenPrice = tradeType.expectedOpenPrice(basePrice,
                                                        delayOffset)

    val stopDist = tradeType * (expectedOpenPrice - stopPosition)

    val stopBoundary = tradeType.stop(stopPosition)

    val takeProfitDist = config.takeProfitFactor * stopDist

    val tpBoundary = tradeType.takeProfit(expectedOpenPrice, takeProfitDist)

    _tradeSet.setBoundaries(stopBoundary, tpBoundary)

    if (stopBoundary.isCrossed(expectedOpenPrice)) {
      context.logger.log("Can't start trade - closed period price range too small")
      return
    }

    if ( ! isStopValidForOpeningTrade(stopDist) ) return
    
    if (_tradeSet.countActiveTrades < config.maxTradesInSerie) {

      val delayBoundary = tradeType.delay(basePrice,
                                          delayOffset)
      val valNumerator = valNumeratorFromStopDistance(stopDist)

      
      val executor = context.tradeBackend.newTradeExecutor(new TradeRequest(valNumerator,
                                                                            tradeType,
                                                                            Some(delayBoundary)))
      _tradeSet.open(executor)
    } else {
      context.logger.log("Maximum trades count reached")
    } 
  }

  private def isStopValidForOpeningTrade(stopDistance:Fraction):Boolean = {
    if ( config.isStopValidForOpeningTrade(stopDistance) ) return true

    context.logger.log("Can't start trade - stop distance too small")

    return false
  }

  private def valNumeratorFromStopDistance(stopDist:Fraction):Int = {
    val riskDenominator = context.tradeBackend.balance * config.oneTradeRiskBalanceFactor

    val valNumeratorRaw = riskDenominator / (stopDist - config.comissionFactor)

    (valNumeratorRaw.floorToInt / config.tradeValueGranularity) * config.tradeValueGranularity
  } 

  private def tradeTypeFor(direction:Direction) = direction match {
    case Up => Buy
    case Down => Sell
    case NoDirection => throw new RuntimeException("Should not happen!")
  }

  private def directionOf(period:Period):Direction = Strategy.directionOf(period, config.directionTolerance)
} 
