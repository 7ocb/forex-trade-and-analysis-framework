package tas.sources.ticks.decomposer

import tas.types.{Interval, PeriodBid, Time, Fraction, TimedBid}


final class PeriodToOpenMinMaxClose(periodInterval:Interval) extends PeriodToTicksDecomposer {

  private val tickInterval = periodInterval / 5

  def decompose(period:PeriodBid):List[TimedBid] = {
    var time = period.time
    List(period.bidOpen,
         period.bidMin,
         period.bidMax,
         period.bidClose)
      .map(tick => new TimedBid({
                                  time += tickInterval
                                  time
                                },
                                tick))
  }

}
