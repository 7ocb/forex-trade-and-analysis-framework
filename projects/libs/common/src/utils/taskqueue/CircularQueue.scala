package tas.utils.taskqueue

private [taskqueue] class CircularBufferedQueue(sizeOfArray:Int) {
  private var readPtr = 0
  private var writePtr = 0
  private var free = sizeOfArray

  private var slots = new Array[QueuedTask](sizeOfArray)

  def increment(original:Int) = {
    val newValue = original + 1

    if (newValue >= sizeOfArray) 0
    else newValue
  }

  def enqueue(task:QueuedTask) = {
    slots(writePtr) = task
    writePtr = increment(writePtr)
    free -= 1
  }

  def dequeue:QueuedTask = {
    if (isEmpty) null
    else {
      val value = slots(readPtr)

      readPtr = increment(readPtr)
      free += 1
      value
    }
  }

  def head:QueuedTask = {
    if (isEmpty) null
    else slots(readPtr)
  }

  def isEmpty = free == sizeOfArray

  def haveSpace = free != 0

  def size = sizeOfArray
}
