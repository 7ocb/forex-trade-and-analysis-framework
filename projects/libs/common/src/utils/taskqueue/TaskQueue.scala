package tas.utils.taskqueue

import tas.timers.Timer

import tas.types.Time

class TaskQueue(currentTime:()=>Time) {

  private type TimeFrameTasks = FastQueue
  private type QueuesList = List[TimeFrameTasks]
  
  private var _queue = List[TimeFrameTasks]()

  private var _timeFramePool = List[TimeFrameTasks]()

  def put(task:QueuedTask):Unit = {

    val timeNow = currentTime()
    
    _queue = add(task, _queue)
  }

  private def compareTasks(first:QueuedTask, last:QueuedTask) = {
    if (first.time eq last.time) 0
    else if (first.time eq Timer.Now) {

      if (last.time eq Timer.Now) 0
      else -1

    }
    else if (last.time eq Timer.Now) 1
    else first.time.compare(last.time)
  }

  private def inNewFrame(task:QueuedTask) = {
    val queue = if (_timeFramePool.isEmpty) {
        new FastQueue
      } else {
        val queue = _timeFramePool.head
        _timeFramePool = _timeFramePool.tail
        queue
      }

    queue.enqueue(task)
    queue
  }

  private def recycle(timeFrame:TimeFrameTasks) = {
    _timeFramePool = timeFrame :: _timeFramePool
  }

  private def add(task:QueuedTask, queue:QueuesList):QueuesList = {
    
    if (queue.isEmpty) List(inNewFrame(task))
    else {
      val first = queue.head.head
      val compareTime = compareTasks(task, first)

      if (compareTime < 0) inNewFrame(task) :: queue
      else if (compareTime == 0) {
        queue.head.enqueue(task)
        queue
      } else queue.head :: add(task, queue.tail)
    }
  }

  final def isEmpty() = _queue.isEmpty

  final def peek():QueuedTask = {
    if (_queue.isEmpty) null
    else _queue.head.head
  }

  final def pop():QueuedTask = {
    if (_queue.isEmpty) null
    else {
      val timeFrame = _queue.head
      val result = timeFrame.dequeue

      if (timeFrame.isEmpty) {
        recycle(timeFrame)
        _queue = _queue.tail
      }

      result
    }
  }

  final def clear() = List[TimeFrameTasks]()
}
