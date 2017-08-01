package tas.sources.periods.remote

import java.io.InputStream
import tas.readers.PeriodsSequence

import tas.types.{Time, PeriodBid, Interval}
import tas.output.logger.Logger
import tas.utils.{IO, StartableOnce}
import tas.concurrency.{RunLoop, NewThreadWorker}

object DownloadPeriodsFromUrl {
  private lazy val worker = new NewThreadWorker

  private val minRetryInterval = Interval.seconds(10)
  private val maxRetryInterval = Interval.minutes(1)
  private val retryStep = Interval.seconds(5)
}

final class DownloadPeriodsFromUrl(runLoop:RunLoop,
                      logger:Logger,
                      requestUrl:String,
                      resultReceiver:List[PeriodBid]=>Unit) extends StartableOnce {
  import tas.utils.IO.withStream
  import DownloadPeriodsFromUrl._

  private var retryInterval = minRetryInterval

  override protected def doStart():Unit = restartDownloading()

  private def restartDownloading():Unit = {
    worker.run(downloadPeriods)
  }

  private def downloadPeriods():Unit = {
    try {
      withStream(IO.urlInputStream(requestUrl),
                 (stream:InputStream) => {
                   // read all data

                   val data = IO.readAll(stream)

                   if (data.length == 0) {
                     // no periods in data
                     postResult(List[PeriodBid]())
                   } else {

                     val periods = PeriodsSequence.fromString(data).all()

                     if (periods.isEmpty) {
                       postRestartDownloading("unexpectedly no periods")
                     } else {
                       postResult(periods)
                     }
                   }
                 } )
    } catch {
      case e:Exception => {
        postRestartDownloading("connection error: " + e)
      }
    }
  }

  private def postResult(result:List[PeriodBid]) = {
    runLoop.post(() => {
                   resultReceiver(result)
                 })
  }

  private def postRestartDownloading(logMessage:String) = {
    runLoop.postDelayed(retryInterval,
                        () => {
                          logger.log(logMessage)
                          restartDownloading()
                        })

    val newRetryInterval = retryInterval + retryStep

    if (newRetryInterval <= maxRetryInterval) {
      retryInterval = newRetryInterval
    }
  }
}
