package tas.utils.taskqueue

import tas.types.Time

case class QueuedTask(time:Time, action:()=>Unit) 

