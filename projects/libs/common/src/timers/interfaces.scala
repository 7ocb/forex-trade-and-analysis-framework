package tas.timers

import tas.types.Time
import tas.types.Interval

object Timer {
  val Now:Time = null
}

abstract trait Timer {

  
  def callAt(time:Time, action:()=>Unit):Unit
  def currentTime:Time

  def at(time:Time)(action: => Unit) = callAt(time, () => action)
  def after(interval:Interval)(action: => Unit) = callAt(currentTime + interval, () => action)
  def run(action: => Unit) = at(Timer.Now)(action)
  
}

 

