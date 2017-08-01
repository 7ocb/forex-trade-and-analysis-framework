package tas.periodstatisticscollector.collectors

import tas.sources.periods.PeriodSource

import tas.types.Fraction

class DerivedCollector(periodsSource:PeriodSource,
                       subcollector:Collector[Fraction]) extends Collector[Fraction] {
  private var lastValue:Option[Fraction] = None
  private var derived:Option[Fraction] = None

  periodsSource.periodCompleted += { period => 

    val prevValue = lastValue

    lastValue = subcollector.value

    derived = for (prev <- prevValue;
                   last <- lastValue) yield last - prev

  }

  def value = derived

  val name = subcollector.name + " derived"
}
