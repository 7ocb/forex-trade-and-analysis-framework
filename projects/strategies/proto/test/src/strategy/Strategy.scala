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

import tas.trading.{
                    TradeBackend, TradeRequest, TradeValue,
                    TradeHandle, CloseCondition,
                    TradeExecutor, Boundary}

import tas.output.logger.Logger

import tas.events.{
  Subscription,
  SyncSubscription
}

import scala.collection.mutable.ListBuffer

import scala.annotation.tailrec

object Strategy {

  trait ConditionOfActiveness {
    def isActive:Boolean
    def changedEvent:Subscription[()=>Unit]
  }

  object AlwaysActive extends ConditionOfActiveness {
    def isActive = true
    def changedEvent = new SyncSubscription[()=>Unit]()
  }

  case class Config(activeness:ConditionOfActiveness,
                    trackPeriods:Int,
                    stopFactor:Fraction,
                    tpFactor:Fraction,
                    minimalStopDistance:Fraction,
                    minimalTpDistance:Fraction,
                    minimalDirectionDifference:Fraction)


  trait Context {
    def tradeBackend:TradeBackend
    def logger:Logger
    def periodsSource:PeriodSource
    def balance:Fraction
  }

  def nextPeriodStartTime(initialTime:Time,
                          period:Interval,
                          shift:Interval) = {

    val startTime = period.findNextStartInDay(initialTime) + shift

    @tailrec def closerToInitialTime(candidate:Time):Time = {
      val shifted = candidate - period
      if (shifted > initialTime) closerToInitialTime(shifted)
      else candidate
    }

    closerToInitialTime(startTime)
  }
} 

import Strategy._

class SlidingStatisticsValue[Type](val count:Int,
                                   val calculation:List[Type]=>Type) {

  private var _calculated:Option[Type] = None
  private var _collected = new ListBuffer[Type]

  def put(newValue:Type):Unit = {
    _collected += newValue

    if (_collected.size > count) {
      _collected = _collected.drop(1)
    }
  }

  def canBeCalculated = _collected.size == count

  def value:Type = {
    if (_calculated != None) _calculated.get

    if (! canBeCalculated) throw new RuntimeException("Can't be calculated, but requested")

    _calculated = Some(calculation(_collected.toList))

    _calculated.get
  }
}

class Strategy(timer:Timer, config:Config, context:Context) {

  def logger = context.logger

  context.periodsSource.periodCompleted += onCompletedPeriod

  config.activeness.changedEvent += onActivenessChange

  def calculateAverage(values:List[Fraction]):Fraction = values.sum / values.size

  private val _toMaxFromOpen = new SlidingStatisticsValue(config.trackPeriods,
                                                          calculateAverage)

  private val _toMinFromOpen = new SlidingStatisticsValue(config.trackPeriods,
                                                          calculateAverage)

  private val _trades = new ListBuffer[TradeExecutor]

  private def onActivenessChange() = {
    if ( ! config.activeness.isActive) {
      context.logger.log("closing all by end of week")

      closeTrades()
    }
  }

  private def onCompletedPeriod(period:Period):Unit = {

    _toMaxFromOpen.put(period.priceMax - period.priceOpen)
    _toMinFromOpen.put(period.priceOpen - period.priceMin)

    if ( ! config.activeness.isActive) return

    if ((! _toMaxFromOpen.canBeCalculated)
          || (! _toMinFromOpen.canBeCalculated)) {
      logger.log("can't calculate - unsufficient history collected")
      return
    }

    logger.log("period started ", period.time,
               ", O: ", period.priceOpen,
               ", C: ", period.priceClose,
               ", L: ", period.priceMin,
               ", H: ", period.priceMax)

    if ((_toMaxFromOpen.value - _toMinFromOpen.value).abs < config.minimalDirectionDifference) {
      logger.log("can't open trade - averages is almost equal")
      return
    }

    // if price mainly going up, open buy, otherwise open sell
    val (direction, base, other) = if (_toMaxFromOpen.value > _toMinFromOpen.value) (Buy, _toMaxFromOpen, _toMinFromOpen)
                                   else (Sell, _toMinFromOpen, _toMaxFromOpen)


    val stopDistance = other.value * config.stopFactor
    val tpDistance = base.value * config.tpFactor

    if (stopDistance < config.minimalStopDistance) {
      logger.log("can't open trade - stop calculated to too small distance: ", stopDistance)
      return
    }

    if (tpDistance < config.minimalTpDistance) {
      logger.log("can't open trade - tp calculated to too small distance: ", tpDistance)
      return
    }

    val trade = context.tradeBackend.newTradeExecutor(new TradeRequest("2000",
                                                                       direction,
                                                                       None))

    trade.openTrade(direction.stop(period.priceClose,
                                   stopDistance),
                    Some(direction.takeProfit(period.priceClose,
                                              tpDistance)),
                    () => { /* do nothing when opened */ },
                    () => {
                      /* remove sell from list when closed */
                      _trades -= trade
                    })
  }

  private def closeTrades() {
    _trades.foreach(_.closeTrade)
    _trades.clear()
  } 

} 
