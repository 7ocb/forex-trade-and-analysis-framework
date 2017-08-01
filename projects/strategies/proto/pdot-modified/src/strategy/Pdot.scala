package tas.strategies.pdot



import tas.output.logger.Logger

import tas.sources.periods.PeriodSource

import tas.sources.ticks.TickSource

import tas.strategies.activeness.ActivenessCondition

import tas.timers.Timer

import tas.trading.{
  TradeBackend,
  TradeExecutor,
  TradeRequest
}

import tas.types.{
  Fraction,
  Period,
  Price,
  Trade, Buy, Sell,
  Boundary
}

object Pdot {

  sealed trait Direction
  object Up extends Direction
  object Down extends Direction

  case class Config(activeness:ActivenessCondition,
                    stopDistance:Fraction,
                    thresholdToDetectDirection:Fraction,
                    value:Fraction)

  trait Context {
    def tradeBackend:TradeBackend
    def tickSource:TickSource
    def periodsSource:PeriodSource
    def logger:Logger
  }
}

import Pdot._

class Pdot(timer:Timer,
           config:Config,
           context:Context) {

  // private var _thisEndDirectionExpected:Direction = null

  private var _trade:TradeExecutor = null
  private var _lastCompletedPeriod:Period = null

  // private var _predictionsSucceed = 0
  // private var _predictionsFailed = 0

  context.periodsSource.periodCompleted += onCompletedPeriod
  context.tickSource.tickEvent += onTick

  config.activeness.changedEvent += onActivenessChange

  private def onActivenessChange() = {
    if ( ! config.activeness.isActive) {
      closeTrade()
      _lastCompletedPeriod = null
    }
  }

  private def onCompletedPeriod(period:Period):Unit = {
    closeTrade()

    // if (_thisEndDirectionExpected != null) {

    //   val direction = if (period.change(Price.Bid) > 0) Up
    //                   else Down

    //   val predictionSucceed = direction == _thisEndDirectionExpected

    //   context.logger.log("expected direction: " + _thisEndDirectionExpected)
    //   context.logger.log("actual direction: " + direction)

    //   if (predictionSucceed) _predictionsSucceed += 1
    //   else _predictionsFailed += 1

    //   context.logger.log("predictionsSucceed: " + _predictionsSucceed)
    //   context.logger.log("predictionsFailed: " + _predictionsFailed)

    //   _thisEndDirectionExpected = null
    // }

    if ( ! config.activeness.isActive) return

    context.logger.log("Period ended: " + period)
    _lastCompletedPeriod = period
    pendingTrade = null
  }

  val priceForCheck = Price.Bid

  case class PendingTrade(openingBoundary:Boundary,
                          trade:Trade)

  var pendingTrade:PendingTrade = null

  private def onTick(price:Price):Unit = {
    if ( ! config.activeness.isActive) return

    if (_lastCompletedPeriod == null) return

    // val lastPeriodClose = _lastCompletedPeriod.priceClose

    // tryOpen(Sell, lastPeriodClose, price)
    // tryOpen(Buy,  lastPeriodClose, price)

    if (pendingTrade != null) {

      if (pendingTrade.openingBoundary.isCrossed(priceForCheck(price))
            && _trade == null) {
        // do open trade

        context.logger.log("returned to period open price: " + price)
        context.logger.log("setting up trade")

        _trade = context.tradeBackend.newTradeExecutor(new TradeRequest(config.value,
                                                                        pendingTrade.trade,
                                                                        None))

        val stop = pendingTrade.trade.stop(price,
                                           config.stopDistance)

        _trade.openTrade(stop,
                         None,
                         () => {},
                         () => {})
      }

    } else {
      pendingTrade = detectPendingTrade(price)
    }
    
  }

  private def detectPendingTrade(price:Price) = {


    val lastPeriodClose = _lastCompletedPeriod.priceClose

    val thresholdFromPreviousClose = priceForCheck(price) - priceForCheck(lastPeriodClose)

    // nothing to do if threshold too small
    if (thresholdFromPreviousClose.abs >= config.thresholdToDetectDirection) {


      context.logger.log("deviation detected on price: " + price)
      context.logger.log("actual deviation: " + thresholdFromPreviousClose)

      val result = if (thresholdFromPreviousClose > 0) {
          new PendingTrade(Boundary <= priceForCheck(lastPeriodClose),
                           Sell)
        } else {
          new PendingTrade(Boundary >= priceForCheck(lastPeriodClose),
                         Buy)
        }

      context.logger.log("will open " + result.trade + " when " + result.openingBoundary)

      result

    } else {
      null
    }


  }

  // private def tryOpen(trade:Trade,
  //                     lastPeriodClose:Price,
  //                     current:Price):Unit = {
  //   if (_trade != null) return

  //   val priceForCheck = Price.Bid

  //   val thresholdFromPreviousClose = priceForCheck(current) - priceForCheck(lastPeriodClose)

  //   // nothing to do if threshold too small
  //   if (thresholdFromPreviousClose.abs < config.thresholdToDetectDirection) return

  //   if (thresholdFromPreviousClose > 0 && trade != Buy) return
  //   if (thresholdFromPreviousClose < 0 && trade != Sell) return

  //   context.logger.log("threshold: ", thresholdFromPreviousClose)

  //   // if (trade == Buy) {
  //   //   _thisEndDirectionExpected = Up
  //   // } else {
  //   //   _thisEndDirectionExpected = Down
  //   // }

  //   // context.logger.log("expected direction: ", _thisEndDirectionExpected)


  //   context.logger.log("lastPeriodClose: " + lastPeriodClose)
  //   context.logger.log("current price: " + current)

  //   val expectedOpenPrice = priceForCheck(lastPeriodClose)

  //   val delay = trade.delay(expectedOpenPrice)

  //   val stop = trade.stop(expectedOpenPrice,
  //                         config.stopDistance)

  //   _trade = context.tradeBackend.newTradeExecutor(new TradeRequest(config.value,
  //                                                                   trade,
  //                                                                   Some(delay)))

  //   _trade.openTrade(stop,
  //                    None,
  //                    () => {},
  //                    () => {})
  // }


  private def closeTrade() = {
    if (_trade != null) {
      _trade.closeTrade()
      _trade = null
    }
  }
}
