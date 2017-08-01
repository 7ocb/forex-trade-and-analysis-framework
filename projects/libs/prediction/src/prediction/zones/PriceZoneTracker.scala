package tas.prediction.zones

import scala.collection.mutable.ListBuffer

import tas.types.{
  Fraction,
  Price,
  Time,
  Sell, Buy,
  TimedTick
}

object PriceZoneTracker {

  trait OpenedZone {
    def leave():Unit
  }

}

final class PriceZoneTracker(tickNow:()=>TimedTick) {
  import PriceZoneTracker._

  private val _zones:ListBuffer[Zone] = new ListBuffer[Zone]

  def enter():OpenedZone = new OpenedZone() {
      val start = tickNow()
      def leave():Unit = {
        val newZone = new Zone(start, tickNow())
        _zones += newZone
      }
    }

  def results() = {
    val sorted = _zones.toList.sortBy(_.start.time)

    (ZonesSet(sorted, "up",   _.upDiff),
     ZonesSet(sorted, "down", _.downDiff))
  }

}
