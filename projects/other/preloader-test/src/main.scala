
import tas.concurrency.RunLoop

import tas.timers.RealTimeTimer
import tas.timers.Timer


object Run extends App {
  val runLoop = new RunLoop

  val rtTimer = new RealTimeTimer(runLoop)

} 
