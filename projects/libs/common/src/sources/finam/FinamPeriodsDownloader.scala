package tas.sources.finam

import tas.sources.periods.remote.DownloadPeriodsFromUrl
import tas.utils.StartableOnce
import tas.concurrency.RunLoop
import tas.output.logger.Logger
import tas.types.{Time, PeriodBid}

object FinamPeriodsDownloader {

  class Range(val start:Time, val end:Time)

  trait Handler {
    def onPeriodsPortion(periods:List[PeriodBid])
    def onNoMorePeriods
  }
}

import FinamPeriodsDownloader.Handler
import FinamPeriodsDownloader.Range

final class FinamPeriodsDownloader(runLoop:RunLoop,
                                   logger:Logger,
                                   from:Time,
                                   to:Time,
                                   pair:Parameters.CurrencyPair,
                                   handler:Handler) extends StartableOnce {

  private var ranges = createRanges(from)
  private var lastProcessed:PeriodBid = null

  def leftRanges = ranges.size

  override protected def doStart() = startNextRangeDownloading()

  private def startNextRangeDownloading():Unit = {

    if (ranges.isEmpty) {
      handler.onNoMorePeriods
    } else {
      val range = ranges.head
      ranges = ranges.tail

      val url = FinamUrl(pair,
                         Parameters.Min1,
                         Parameters.dateFromTime(range.start),
                         Parameters.dateFromTime(range.end))

      new DownloadPeriodsFromUrl(runLoop,
                                 logger,
                                 url,
                                 onPeriodsList)
        .start()
    }
  }

  private def isPeriodShouldBePassed(period:PeriodBid) = {

    val periodTime = period.time

    val notProcessedBefore = lastProcessed == null || periodTime > lastProcessed.time
    val inTimeBounds = periodTime <= to.startOfNextDay && periodTime >= from

    inTimeBounds && notProcessedBefore
  }

  private def onPeriodsList(periods:List[PeriodBid]) = {

    val periodsToDispatch = periods.filter(isPeriodShouldBePassed)

    if ( ! periodsToDispatch.isEmpty ) {
      handler.onPeriodsPortion(periodsToDispatch)
      lastProcessed = periodsToDispatch.last
    }
    startNextRangeDownloading()
  }

  private def createRanges(start:Time):List[Range] = {
    val after = start.shifted(shiftMonths = 1)
    val inOneRange = after.shifted(shiftDays = 15)
    if (inOneRange >= to) List(new Range(start, to))
    else {
      val thisRange = new Range(start, after.shifted(shiftDays = -1))
      thisRange::createRanges(after)
    }
  }

}
