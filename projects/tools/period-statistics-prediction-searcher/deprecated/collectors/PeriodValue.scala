package tas.periodstatisticscollector.collectors

import tas.sources.periods.PeriodSource

import tas.types.Period

class PeriodValue[T](periodsSource:PeriodSource,
                     val name:String,
                     calculate:Period=>T) extends Collector[T] {
  periodsSource.periodCompleted += { period =>
    last = Some(calculate(period))
  }

  private var last:Option[T] = None

  def value = last
}
