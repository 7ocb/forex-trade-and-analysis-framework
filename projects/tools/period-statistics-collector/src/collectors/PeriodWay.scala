package tas.periodstatisticscollector.collectors

import tas.sources.periods.PeriodSource

import tas.types.{
  Price,
  Fraction
}


class PeriodWay(periodsSource:PeriodSource,
                subperiodsSource:PeriodSource,
                priceAccessor:Price.Accessor) extends Collector[Fraction] {

  private var subperiodsRange:Fraction = Fraction.ZERO
  private var last:Option[Fraction] = None

  periodsSource.periodCompleted += { periodIgnored =>
    last = Some(subperiodsRange)
    subperiodsRange = Fraction.ZERO
  }

  subperiodsSource.periodCompleted += { subperiod =>
    subperiodsRange += subperiod.range(priceAccessor)
  }

  def value = last

  val name = "way"
}
