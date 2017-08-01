package tas.readers

import tas.types.{
  Time,
  Interval,
  TimedBid
}

import tas.input.Sequence
import tas.input.FileMetrics

import tas.input.format.ticks.metatrader.MetatraderExportedTicks
import tas.input.format.ticks.bincache.TicksBinaryCache

class TicksFileMetrics protected[TicksFileMetrics](val firstTickTime:Time,
                                                   val lastTickTime:Time)

object TicksFileMetrics extends FileMetrics[TimedBid, TicksFileMetrics] {

  def format = MetatraderExportedTicks
  def cacheFormat = TicksBinaryCache

  def fromSequence(seq:Sequence[TimedBid]) = {

    var firstTickTime:Time = null
    var lastTickTime:Time = null
    
    seq.foreach(tick => {
                  if (firstTickTime == null) {
                    firstTickTime = tick.time
                  }

                  lastTickTime = tick.time
                } )

    new TicksFileMetrics(firstTickTime, lastTickTime)
  }

}
