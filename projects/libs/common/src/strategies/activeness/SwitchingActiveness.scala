package tas.strategies.activeness

import tas.timers.Timer
import tas.types.Time

import tas.events.SyncCallSubscription

object SwitchingActiveness {
  case class Switch(val timeOfSwitch:Time, val willBeActiveAfterSwitch:Boolean)
}


abstract class SwitchingActiveness(timer:Timer, startTime:Time) extends ActivenessCondition {
  import SwitchingActiveness.Switch

  private var nextExpectedSwitch:Switch = null
  private val _changedEvent = new SyncCallSubscription

  timer.callAt(startTime,
               postNextSwitch)

  private def postNextSwitch():Unit = {
    nextExpectedSwitch = nextSwitch
    timer.callAt(nextExpectedSwitch.timeOfSwitch,
                 () => {
                   postNextSwitch()
                   _changedEvent()
                 } )
  }

  protected def nextSwitch:Switch

  final def isActive = ( nextExpectedSwitch != null
                          && ! nextExpectedSwitch.willBeActiveAfterSwitch )

  final def changedEvent = _changedEvent
}
