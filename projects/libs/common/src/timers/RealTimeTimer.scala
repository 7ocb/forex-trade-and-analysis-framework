package tas.timers


import tas.concurrency.RunLoop
import tas.types.Time
import java.util.Date
import java.util.Calendar

class RealTimeTimer(val runLoop:RunLoop) extends Timer {

  def callAt(time:Time, action:()=>Unit):Unit = {
    if (time == Timer.Now) {
      runLoop.post(action)
    } else {
      runLoop.postDelayed(currentTime - time,
                          action)
    } 
  }
  
  def currentTime:Time = Time.now
} 
