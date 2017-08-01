package tas.timers

import tas.types.Time
import tas.types.Interval
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.Queue
import scala.math.Ordering
import scala.annotation.tailrec
import tas.utils.taskqueue.TaskQueue
import tas.utils.taskqueue.QueuedTask


object JustNowFakeTimer {
  def apply[T](initfunc: (JustNowFakeTimer)=>T):T = {
    val timer = new JustNowFakeTimer

    val value = initfunc(timer)
    timer.loop

    value
  } 
}




class JustNowFakeTimer extends Timer {

  private var _stopped = false

  private var _queue = new TaskQueue(currentTime _)
  // private var _currentTimeFrame:ListBuffer[Task] = null
  private var _currentTime:Time = Time.milliseconds(0)

  override def callAt(time:Time, action:()=>Unit):Unit = {
    _queue.put(new QueuedTask(time, action))
  } 

  override def currentTime:Time = _currentTime

  final def stop() = _stopped = true

  @tailrec final def loop():Unit = {
    if (_stopped) return

    val task = _queue.pop()
    if (task != null) {
      val newCurrentTime = task.time
      if (newCurrentTime != null) {
        _currentTime = newCurrentTime
      }
      task.action()
      loop()
    } 

  }

}
