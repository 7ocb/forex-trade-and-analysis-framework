package tas.sources.periods

import tas.events.Event
import tas.types.Period

trait PeriodSource {
  def periodCompleted:Event[Period]
  def periodUpdated:Event[Period]
  def emptyPeriodEnded:Event[Unit]
} 
