package tas.prediction.zones

import java.io.Serializable

import tas.types.{
  TimedTick,
  Buy,
  Sell
}

case class Zone(start:TimedTick, end:TimedTick) extends Serializable {
  @transient
  lazy val upDiff   = Buy.closePrice(end.price) - Buy.openPrice(start.price)

  @transient
  lazy val downDiff = Sell.openPrice(start.price) - Sell.closePrice(end.price)

  override def equals(o:Any) = o match {
      case that:Zone => that.start == start && that.end == end
      case _ => false
    }

  override def toString() = "Zone(" + start + ", " + end + ")"

}



