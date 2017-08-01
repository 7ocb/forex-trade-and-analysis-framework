package tas.sources.ticks

import tas.types.{Time}
import tas.output.logger.Logger
import tas.concurrency.RunLoop

import tas.sources.periods.remote.DownloadPeriodsFromUrl

class GetterDownloader(runLoop:RunLoop,
                       urlFactory:Option[Time]=>String,
                       logger:Logger) extends TicksFromRemotePeriods.PeriodsGetter {

  def request(startPeriodStartTime:Option[Time]):Unit = {

    val requestUrl = urlFactory(startPeriodStartTime)

    new DownloadPeriodsFromUrl(runLoop,
                               logger,
                               requestUrl,
                               passResult).start()
  } 
}
