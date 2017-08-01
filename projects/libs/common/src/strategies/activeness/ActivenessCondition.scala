package tas.strategies.activeness

import tas.timers.Timer
import tas.types.Time

import tas.events.Subscription

trait ActivenessCondition {

  /**
    * If strategy expected to be active right now
    */
  def isActive:Boolean

  /**
    * This event is fired every time activeness changed
    */
  def changedEvent:Subscription[()=>Unit]

}
