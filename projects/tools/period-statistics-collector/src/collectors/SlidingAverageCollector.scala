package tas.periodstatisticscollector.collectors

import scala.collection.mutable.ListBuffer

import tas.sources.periods.PeriodSource

import tas.types.Fraction

class SlidingAverageCollector(periodsSource:PeriodSource,
                              steps:Int,
                              subcollector:Collector[Fraction]) extends Collector[Fraction] {
  private var collected = new ListBuffer[Option[Fraction]]
  private var lastValue:Option[Fraction] = None

  periodsSource.periodCompleted += { period => 
    collected += subcollector.value

    if (collected.size > steps) collected = collected.drop(1)

    if (collected.contains(None)) {
      lastValue = None
    } else {
      lastValue = Some(collected.map(_.get).sum / steps)
    }
  }

  def value = lastValue

  val name = subcollector.name + " sliding average"
}
