package tas.strategies.pdot


import tas.output.logger.{
  PrefixTimerTime,
  Logger
}

import tas.prediction.Prediction

import tas.readers.TicksFileMetrics

import tas.sources.periods.PeriodSource

import tas.sources.ticks.TickSource

import tas.strategies.activeness.ActivenessCondition

import tas.timers.{
  Timer,
  JustNowFakeTimer
}

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
                    // stopDistance:Fraction,
                    deviationToDetectDirection:Fraction// ,
                    // value:Fraction
  )

  trait Context {
    def prediction:Prediction
    def tickSource:TickSource
    def periodsSource:PeriodSource
    def logger:Logger
  }
}

import Pdot._

class Pdot(timer:Timer,
           config:Config,
           context:Context) {

  private var _lastCompletedPeriod:Period = null

  context.periodsSource.periodCompleted += onCompletedPeriod
  context.tickSource.tickEvent += onTick

  config.activeness.changedEvent += onActivenessChange

  private def onActivenessChange() = {
    if ( ! config.activeness.isActive) {
      context.logger.log("deactivated")
      doLeavePredictionZone()
      _lastCompletedPeriod = null
    } else {
      context.logger.log("activated")
    }
  } 

  private def onCompletedPeriod(period:Period):Unit = {

    doLeavePredictionZone()

    if ( ! config.activeness.isActive) return

    context.logger.log("Period ended: " + period)
    
    _lastCompletedPeriod = period
    enteringPredictionZone = null
  }

  val priceForCheck = Price.Bid

  case class PendingTrade(openingBoundary:Boundary,
                          trade:Trade)

  var enteringPredictionZone:Boundary = null

  var predictionZone:Prediction.Zone = null

  private def isInPredictionZone = predictionZone != null

  private def doEnteringPredictionZone():Unit = {
    if (isInPredictionZone) return

    context.logger.log("entering prediction zone")

    predictionZone = if (enteringPredictionZone.cmp.isBelow) {
        context.prediction.expectDown()
      } else {
        context.prediction.expectUp()
      }
  }

  private def doLeavePredictionZone():Unit = {
    if (!isInPredictionZone) return

    context.logger.log("leaving prediction zone")

    predictionZone.leave()
    predictionZone = null
  }

  private def onTick(price:Price):Unit = {
    if ( ! config.activeness.isActive) return

    if (_lastCompletedPeriod == null) return

    if (enteringPredictionZone != null) {

      if (enteringPredictionZone.isCrossed(priceForCheck(price))
            && !isInPredictionZone) {

        context.logger.log("returned to period open price: " + price)

        doEnteringPredictionZone()
      }

    } else {
      enteringPredictionZone = detectEnterPredictionZone(price)
    }
    
  }

  private def detectEnterPredictionZone(price:Price) = {


    val lastPeriodClose = _lastCompletedPeriod.priceClose

    val deviationFromPreviousClose = priceForCheck(price) - priceForCheck(lastPeriodClose)

    // nothing to do if deviation too small
    if (deviationFromPreviousClose.abs >= config.deviationToDetectDirection) {


      context.logger.log("deviation detected on price: " + price)
      context.logger.log("actual deviation: " + deviationFromPreviousClose)

      val result = if (deviationFromPreviousClose > 0) Boundary <= priceForCheck(lastPeriodClose)
                   else                                Boundary >= priceForCheck(lastPeriodClose)

      context.logger.log("will enter prediction zone when " + result)

      result

    } else {
      null
    }

  }
}
