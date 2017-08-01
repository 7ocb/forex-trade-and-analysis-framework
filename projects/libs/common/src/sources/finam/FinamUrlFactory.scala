package tas.sources.finam

import tas.types.Time
import tas.types.Interval
import tas.timers.Timer

class FinamUrlFactory(timer:Timer) extends (Option[Time]=>String) {
  def apply(startPeriodStartTime:Option[Time]) = {
    FinamUrl(Parameters.EUR_USD,
             Parameters.Min1,
             Parameters.dateFromTime(if (startPeriodStartTime.isDefined) startPeriodStartTime.get
                                     else timer.currentTime - Interval.days(1)),
             Parameters.dateFromTime(timer.currentTime + Interval.days(1)))
  } 
} 
