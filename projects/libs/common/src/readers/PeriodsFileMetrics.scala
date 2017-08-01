package tas.readers

import tas.types.{
  Time,
  Interval,
  PeriodBid
}

import tas.input.Sequence
import tas.input.FileMetrics

import tas.input.format.periods.text.PeriodsText
import tas.input.format.periods.bincache.PeriodsBinaryCache

class PeriodsFileMetrics protected[PeriodsFileMetrics](val firstStartTime:Time,
                                                       val lastStartTime:Time,
                                                       val interval:Interval)

object PeriodsFileMetrics extends FileMetrics[PeriodBid, PeriodsFileMetrics] {

  def format = PeriodsText
  def cacheFormat = PeriodsBinaryCache

  def fromSequence(seq:Sequence[PeriodBid]) = {

    var firstStartTime:Time = null
    var previousStartTime:Time = null
    var lastStartTime:Time = null
    var interval:Interval = null
    
    seq.foreach(period => {
                  if (firstStartTime == null) {
                    firstStartTime = period.time
                  }

                  if (previousStartTime != null) {

                    val lastInterval = period.time - previousStartTime
                    
                    if (interval == null) {
                      interval = lastInterval
                    } else if (interval > lastInterval) {
                      interval = lastInterval
                      if (interval.isZero) {
                        throw new RuntimeException("zero interval found after period: " + previousStartTime)
                      }
                    }
                  }

                  previousStartTime = period.time

                  lastStartTime = period.time
                  
                } )

    new PeriodsFileMetrics(firstStartTime, lastStartTime, interval)
  }

}
