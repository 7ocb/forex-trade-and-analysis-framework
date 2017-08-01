package tas.strategies.activeness

import tas.timers.Timer

import tas.types.Time.Moscow
import tas.types.{
  Interval,
  Time,
  Saturday,
  Sunday,
  Monday,
  Friday
}

class DefaultActiveness(timer:Timer, startTime:Time) extends SwitchingActiveness(timer, startTime) {

  import SwitchingActiveness.Switch

  override protected final def nextSwitch = {
    val utcTime = timer.currentTime

    def nextActivationUtc = utcTime.fromUtcTo(Moscow).nextTime(Monday, Interval.time(0, 2, 0, 0)).toUtcFrom(Moscow)

    def nextDeactivationUtc = utcTime.nextTime(Friday, Interval.time(0, 19, 50, 0))

    val currentlyActive = nextDeactivationUtc < nextActivationUtc

    new Switch(if (currentlyActive) nextDeactivationUtc
               else nextActivationUtc,
               ! currentlyActive)
  }
}
