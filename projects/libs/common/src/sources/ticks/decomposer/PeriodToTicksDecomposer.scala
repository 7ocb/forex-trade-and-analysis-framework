package tas.sources.ticks.decomposer

import tas.types.{
  PeriodBid,
  TimedBid
}

trait PeriodToTicksDecomposer {
  def decompose(period:PeriodBid):List[TimedBid]
}
