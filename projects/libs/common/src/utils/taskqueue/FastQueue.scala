package tas.utils.taskqueue

import scala.annotation.tailrec

private [taskqueue] class FastQueue {
  private val initialSize = 5
  private val increaseStep = 5

  var queue = new CircularBufferedQueue(initialSize)

  def enqueue(task:QueuedTask) = {
    val needToIncrease = !queue.haveSpace
    if (needToIncrease) {

      val newSize = queue.size + increaseStep
      val oldQueue = queue
      queue = new CircularBufferedQueue(newSize)

      @tailrec def requeue:Unit = {
        val taskToRequeue = oldQueue.dequeue
        if (taskToRequeue != null) {
          queue.enqueue(taskToRequeue)
          requeue
        }
      }

      requeue
    }
    
    queue.enqueue(task)
  }

  def head = queue.head

  def isEmpty = queue.isEmpty

  def dequeue:QueuedTask = queue.dequeue
}
