package tas.types

object PeriodBid {
  def apply(period:Period):PeriodBid = new PeriodBid(period.priceOpen.bid,
                                                     period.priceClose.bid,
                                                     period.priceMin.bid,
                                                     period.priceMax.bid,
                                                     period.time)
}

class PeriodBid(val bidOpen:Fraction,
                val bidClose:Fraction,
                val bidMin:Fraction,
                val bidMax:Fraction,
                val time:Time) {

  override def toString():String = {
    "bids: open: " + bidOpen + " close: " + bidClose + " min: " + bidMin + " max: " + bidMax + " (at " + time + ")"
  }

  override def equals(o:Any) = {
    if (o.isInstanceOf[PeriodBid]) {
      val that = o.asInstanceOf[PeriodBid]

      (bidOpen == that.bidOpen
         && bidClose == that.bidClose
         && bidMin == that.bidMin
         && bidMax == that.bidMax
         && time == that.time)
    } else false
  }

  lazy val range = bidMax - bidMin

  lazy val change = bidClose - bidOpen
}

