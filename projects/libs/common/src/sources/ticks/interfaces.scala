package tas.sources.ticks

import tas.events.Event
import tas.types.{
  Fraction,
  Price
}

trait TickSource {
  def tickEvent:Event[Price]
}
