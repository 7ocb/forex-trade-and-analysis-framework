package tas.types

object Period {
  def fromBid(periodBid:PeriodBid, spread:Fraction) = {
    def createPrice(getter:(PeriodBid=>Fraction)) = Price.fromBid(getter(periodBid),
                                                                  spread)

    new Period(createPrice(_.bidOpen),
               createPrice(_.bidClose),
               createPrice(_.bidMin),
               createPrice(_.bidMax),
               periodBid.time)
  }
}

class Period(val priceOpen:Price,
             val priceClose:Price,
             val priceMin:Price,
             val priceMax:Price,
             val time:Time) {

  override def toString():String = {
    "open: " + priceOpen + " close: " + priceClose + " min: " + priceMin + " max: " + priceMax + " (at " + time + ")"
  }

  override def equals(o:Any) = {
    if (o.isInstanceOf[Period]) {
      val that = o.asInstanceOf[Period]

      (priceOpen == that.priceOpen
       && priceClose == that.priceClose
       && priceMin == that.priceMin
       && priceMax == that.priceMax
       && time == that.time)
    } else false
  }

  def range(getter:(Price)=>Fraction) = getter(priceMax) - getter(priceMin)

  def change(getter:(Price)=>Fraction) = getter(priceClose) - getter(priceOpen)

} 

