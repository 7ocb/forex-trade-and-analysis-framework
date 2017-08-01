package tas.sources.ticks

import tas.types.{
  Interval,
  Price
}
import tas.timers.Timer
import tas.events.Event

class TimeShiftTickSource(timer:Timer, val shiftInterval:Interval, slave:TickSource) extends TickSource {

  private val _event = Event.newSync[Price]

  slave.tickEvent += onTick

  private def onTick(tick:Price) = {
    if (shiftInterval.isZero) {
      _event << tick
    } else {
      timer.after(shiftInterval) {
        _event << tick
      } 
    } 
  } 
  
  
  def tickEvent:Event[Price] = _event
  
} 
