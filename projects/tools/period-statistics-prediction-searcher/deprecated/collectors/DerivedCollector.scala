package tas.periodstatisticscollector.collectors

import tas.sources.periods.PeriodSource

import tas.types.Fraction

class DeltaCollector(periodsSource:PeriodSource,
                       subcollector:Collector[Fraction]) extends Collector[Fraction] {
  private var lastValue:Option[Fraction] = None
  private var delta:Option[Fraction] = None

  periodsSource.periodCompleted += { period => 

    val prevValue = lastValue

    lastValue = subcollector.value

    delta = for (prev <- prevValue;
                   last <- lastValue) yield last - prev

  }

  def value = delta

  val name = "delta(" + subcollector.name + ")"
}
