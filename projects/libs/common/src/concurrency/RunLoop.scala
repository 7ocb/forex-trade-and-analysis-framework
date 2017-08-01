package tas.concurrency


import scala.collection.mutable.ListBuffer
import scala.annotation.tailrec
import tas.types.{
  Interval,
  Time
}

import tas.utils.taskqueue.{
  QueuedTask,
  TaskQueue
}

object RunLoop {
  type Task = ()=>Unit


  def withRunLoop(function:(RunLoop)=>Unit):Unit = {
    val loop = new RunLoop()
    function(loop)
    loop()
  }

  trait DelayedTask {
    def cancel()
  }

  private [RunLoop] class DelayedTaskImpl (task:Task) extends Task with DelayedTask {

    private var cancelled = false

    def cancel() = cancelled = true

    def apply():Unit = if ( ! cancelled) task()
  }
}

final class RunLoop {
  import RunLoop.{Task, DelayedTask, DelayedTaskImpl}

  
  // the task, which marks to stop
  private val terminator:()=>Unit = null

  private val now:Interval = Interval.ZERO

  private val tasks = new TaskQueue(Time.now _)

  def post(func:Task):Unit = {
    postInner(func, now)
  }

  def postDelayed(offset:Interval, func:Task):DelayedTask = {
    val task = new DelayedTaskImpl(func)
    postInner(task, offset)
    task
  }

  private def postInner(func:Task, offset:Interval):Unit = {
    synchronized {

      val now = Time.now

      val timeOfCall = now + offset

      tasks.put(new QueuedTask(timeOfCall, func))

      notify()
    }
  }

  @tailrec private def waitForNextTask:Option[QueuedTask] = {
    synchronized {
      if (tasks.isEmpty) {
        wait
        return waitForNextTask
      } else {
        val task = tasks.peek()

        val now = Time.now

        if (now >= task.time) {
          return Some(tasks.pop())
        } else {
          val waitTime = task.time - now
          wait(waitTime.milliseconds)
          return waitForNextTask
        }
      }
    }
  }

  @tailrec final def apply():Unit = {
    val task = waitForNextTask

    if (task.isDefined) {
      val func = task.get.action
      if (func != terminator) {
        func()
        apply()
      } else {
        tasks.clear()
      }
    }
  }

  /**
   * Post exit message on loop
   */
  private def postExit() {
    postInner(terminator, Interval.ZERO)
  }

  /**
   * Complete processing current and exit imidiately
   */
  def terminate() {
    synchronized {
      tasks.clear
      postExit
    }
  }
}
