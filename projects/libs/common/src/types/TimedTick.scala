package tas.types

final case class TimedTick(val time:Time,
                           val price:Price) {

  override def equals(o:Any) = o match {
      case that:TimedTick => that.time == time && that.price == price
      case _ => false
    }

  override def toString() = "Tick(" + time + ", " + price + ")"
    
}
