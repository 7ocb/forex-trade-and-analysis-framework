package tas.sources.ticks

import tas.events.Event
import tas.input.Sequence

import tas.sources.ticks.decomposer.PeriodToTicksDecomposer
import tas.sources.ticks.utils.SimulatedTicksDispatcher

import tas.timers.Timer

import tas.types.{
  PeriodBid,
  Time,
  Price,
  Interval,
  Fraction
}

final class PeriodsToTicksSource(timer:Timer,
                                 spread:Fraction,
                                 periodDecomposer:PeriodToTicksDecomposer,
                                 _source:Sequence[PeriodBid]) extends TickSource {

  private val _tickEvent = Event.newAsync[Price](timer)
  
  decomposeNextPeriod()

  override def tickEvent:Event[Price] = _tickEvent

  private def dispatchTick(tick:Price) = _tickEvent << tick

  def decomposeNextPeriod() {
    if (_source.haveNext) {

      val period = _source.next

      new SimulatedTicksDispatcher(timer,
                                   periodDecomposer.decompose(period).map(_.tick(spread)),
                                   dispatchTick,
                                   decomposeNextPeriod)

    } 
  } 


} 
