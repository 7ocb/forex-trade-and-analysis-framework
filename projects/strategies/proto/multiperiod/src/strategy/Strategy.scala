package tas.multiperiod.strategy

import tas.{ActiveValue, NotBound, Bound}

import tas.sources.ticks.{
  TickSource,
  PriceTick
}
import tas.types.{
  Time,
  Interval,
  Fraction,
  TradeType, Sell, Buy
}

import tas.sources.PeriodDirection
import tas.sources.PeriodDirection.{Direction, Up, Down, NoDirection}

import tas.timers.Timer

import tas.trading.{TradeBackend, TradeRequest, TradeValue,
                    TradeHandle, CloseCondition,
                    TradeExecutor, Boundary}

import tas.output.logger.Logger

import tas.strategies.activeness.ActivenessCondition

import tas.events.{
  Subscription,
  SyncSubscription
}

import scala.annotation.tailrec

import scala.collection.mutable.{
  ListBuffer,
  LinkedList
}


object Strategy {

  case class Config(activeness:ActivenessCondition,
                    oneTradeRiskBalanceFactor:Fraction,
                    stopDistance:Fraction,
                    enterDirection:EnterDirection,
                    comissionFactor:Fraction,
                    tradeValueGranularity:Int,
                    directionDetectionTolerance:Fraction,
                    intervals:List[Interval])

  trait Context {
    def tradeBackend:TradeBackend
    def logger:Logger
    def tickSource:TickSource
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

  class PeriodTracker(timer:Timer,
                      interval:Interval,
                      directionDetectionTolerance:Fraction) {

    import PeriodDirection.Direction

    import PeriodDirection.Up
    import PeriodDirection.Down
    import PeriodDirection.NoDirection


    case class Stored(val price:Fraction,
                      val timeStored:Time)

    var oldestTick:LinkedList[Stored] = null
    var newestTick:LinkedList[Stored] = null

    // val storedTicks = new ListBuffer[Stored]

    def onTick(price:Fraction):Unit = {

      val now = timer.currentTime

      val node = LinkedList(new Stored(price,
                                       now))

      if (oldestTick == null) {
        oldestTick = node
        newestTick = node
      } else {
        val previousNewest = newestTick
        newestTick = node

        previousNewest.next = newestTick
      }

      def isYoungerThanNeeded(stored:Stored) = (now - stored.timeStored) < interval
      
      while (oldestTick.next.next != oldestTick.next
               && ! isYoungerThanNeeded(oldestTick.next.head)) {
        oldestTick = oldestTick.next
      }
    }

    def direction:Direction = {
      // if nothing stored - no direction
      if (oldestTick == null) NoDirection
      // this is case if we not stored enouhg ticks
      else if ((timer.currentTime - oldestTick.head.timeStored) < interval) NoDirection
      else {
        val difference = newestTick.head.price - oldestTick.head.price

        if (difference.abs < directionDetectionTolerance) NoDirection
        else if (difference > 0) Up
        else Down
      }
    }

    def reset():Unit = {
      oldestTick = null
      newestTick = null
    }
  }

} 

import Strategy._

class Strategy(timer:Timer, config:Config, context:Context) {

  val periodTrackers = config.intervals.map(interval => {
                                              new PeriodTracker(timer,
                                                                interval,
                                                                config.directionDetectionTolerance)
                                            } )

  config.activeness.changedEvent += onActivenessChange

  context.tickSource.tickEvent += onTick

  private var _currentTrade:TradeExecutor = null
  private var _currentTradeType:TradeType = null


  private def onActivenessChange() = {
    if ( ! config.activeness.isActive) {
      closeTrade()
      periodTrackers.foreach(_.reset())
    }
  }

  private def onTick(tick:PriceTick):Unit = {
    if ( ! config.activeness.isActive) return

    val price = tick.price

    periodTrackers.foreach(_.onTick(price))

    val directions = periodTrackers.map(_.direction).distinct

    // have different directions
    if (directions.size > 1) return

    val direction = directions.head

    // no direction - nothing to do
    if (direction == PeriodDirection.NoDirection) return

    val tradeType = tradeTypeFor(direction)

    if (_currentTradeType != tradeType) {
      closeTrade()
    } else {
      return
    }

    _currentTradeType = tradeType

    val value = valNumeratorFromStopDistance(config.stopDistance)

    val basePrice = price

    _currentTrade = context.tradeBackend.newTradeExecutor(new TradeRequest(value,
                                                                           tradeType,
                                                                           None))

    _currentTrade.openTrade(tradeType.stop(basePrice,
                                           config.stopDistance),
                            None,
                            () => { /* do nothing if opened */ },
                            () => { /* do nothing if closed */ })
  }

  private def closeTrade() {
    _currentTradeType = null

    if (_currentTrade != null) {
      _currentTrade.closeTrade
      _currentTrade = null
    }
  } 

  private def tradeTypeFor(direction:Direction) = (direction, config.enterDirection) match {
      case (Up, Direct) => Buy
      case (Down, Direct) => Sell
      case (Up, Opposite) => Sell
      case (Down, Opposite) => Buy
      case _ => throw new RuntimeException("Should not happen!")
    }

  private def valNumeratorFromStopDistance(stopDist:Fraction):Int = {
    val riskDenominator = context.balance * config.oneTradeRiskBalanceFactor

    val valNumeratorRaw = riskDenominator / (stopDist - config.comissionFactor)

    (valNumeratorRaw.floorToInt / config.tradeValueGranularity) * config.tradeValueGranularity
  }
} 
