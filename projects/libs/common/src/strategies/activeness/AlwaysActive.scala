package tas.strategies.activeness

import tas.timers.Timer
import tas.types.Time

import tas.events.SyncSubscription

object AlwaysActive extends ActivenessCondition {

  def isActive = true
  lazy val changedEvent = new SyncSubscription[()=>Unit]()

}
