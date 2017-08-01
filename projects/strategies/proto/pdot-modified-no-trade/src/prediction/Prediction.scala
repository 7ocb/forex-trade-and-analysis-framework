package tas.prediction

import tas.output.logger.Logger

import tas.sources.ticks.TickSource

import tas.timers.Timer

import tas.types.{
  Fraction,
  Price,
  DayOfWeek, Monday, Tuesday, Wednesday, Thursday, Friday, Saturday, Sunday
}

object Prediction {
  trait Zone {
    def leave()
  }

  private trait Direction

  private object Up extends Direction {
    override def toString = "up"
  }

  private object Down extends Direction {
    override def toString = "down"
  }
}

class Prediction(val timer:Timer, val logger:Logger, val ticksSource:TickSource) {

  import Prediction.{
    Direction, Up, Down
  }

  private val priceForCheck = Price.Bid

  private class Statistics {
    var predictionsCount = 0

    var summaryChanges:Fraction = 0
    var summaryAbsChanges:Fraction = 0

    var succeedCount = 0
    var failedCount = 0

    def onPredictionEnded(change:Fraction) = {
      predictionsCount += 1

      if (change > 0) succeedCount += 1
      if (change < 0) failedCount += 1

      summaryChanges += change
      summaryAbsChanges += change.abs
    }
  }

  private var lastPrice:Price = null

  private var zoneId = -1

  private val forDayOfWeek = Map[DayOfWeek,
                                 Statistics]((Monday,    new Statistics),
                                             (Tuesday,   new Statistics),
                                             (Wednesday, new Statistics),
                                             (Thursday,  new Statistics),
                                             (Friday,    new Statistics),
                                             (Saturday,  new Statistics),
                                             (Sunday,    new Statistics))

  private val overallStatistics = new Statistics

  ticksSource.tickEvent += { price => lastPrice = price }

  private class PredictionZone(zoneId:Int,
                               enterPrice:Price,
                               direction:Direction,
                               statisticsForThisZone:List[Statistics]) extends Prediction.Zone {

    def leave():Unit = {

      val thisZoneChange = {
        val actualChange = priceForCheck(lastPrice) - priceForCheck(enterPrice)
        direction match {
          case Up => actualChange
          case Down => -actualChange
        }
      }



      statisticsForThisZone.foreach(_.onPredictionEnded(thisZoneChange))

      logger.log("leaved " + zoneId
                   + " " + direction + " pzone, price: " + lastPrice +
                   ", change: " + thisZoneChange +
                   ", summary: " + overallStatistics.summaryChanges)
    }
  }

  private def enterZone(direction:Direction):Prediction.Zone = {
    val statisticsForThisZone = List(forDayOfWeek(timer.currentTime.dayOfWeek),
                                     overallStatistics)

    zoneId += 1

    logger.log("entered " + zoneId + " " + direction + " pzone on price: " + lastPrice)

    new PredictionZone(zoneId,
                       lastPrice,
                       direction,
                       statisticsForThisZone)
  }

  final def expectUp():Prediction.Zone = enterZone(Up)
  final def expectDown():Prediction.Zone = enterZone(Down)

  private final def dumpStatistics(prefix:String, logger:Logger, statistics:Statistics) = {
    logger.log(prefix + " predictions: " + statistics.predictionsCount)
    logger.log(prefix + " succeed: " + statistics.succeedCount)
    logger.log(prefix + " succeed/predictions: " + (Fraction(statistics.succeedCount)/statistics.predictionsCount))
    logger.log(prefix + " failed: " + statistics.failedCount)
    logger.log(prefix + " failed/predictions: " + (Fraction(statistics.failedCount)/statistics.predictionsCount))

    logger.log(prefix + " while in pzone: ")
    logger.log(prefix + " sum of price changes: " + statistics.summaryChanges)
    logger.log(prefix + " sub of abs of price changes: " + statistics.summaryAbsChanges)
    logger.log(prefix + " price path to change ratio: " + (statistics.summaryChanges/statistics.summaryAbsChanges).abs )
  }

  final def dumpResultTo(logger:Logger) = {
    logger.log("==================================")
    dumpStatistics("overally",
                   logger,
                   overallStatistics)

    logger.log("--- day statistics ")

    List(Monday, Tuesday, Wednesday, Thursday, Friday)
      .foreach(day => {
                 logger.log("---")
                 dumpStatistics(day.toString,
                                logger,
                                forDayOfWeek(day))
               } )
  }
}
