package tas.testing

import tas.timers.Timer
import tas.timers.JustNowFakeTimer

trait TimerSupport {

  private var _timer:Timer = null

  def timer = _timer

  def timerRun(body: => Unit) = {
    try {
      JustNowFakeTimer (
        t => {
          _timer = t
          body
        })
    } finally {
      _timer = null
    }
  }
  
} 
