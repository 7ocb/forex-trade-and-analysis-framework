package tas.types


object Price {
  // TODO: test: Price.fromBid(bid, spread), bid < ask
  def fromBid(bid:Fraction, spread:Fraction) = new Price(bid,
                                                         bid + spread)

  val ZERO = new Price(0, 0)


  type Accessor = (Price)=>Fraction

  val Bid:Accessor = _.bid
  val Ask:Accessor = _.ask
}

final case class Price(val bid:Fraction,
                       val ask:Fraction) {
  if (bid > ask) throw new IllegalArgumentException("bid must be <= ask")

  // TODO: test
  def +(added:Fraction) = new Price(bid + added,
                                    ask + added)

  // TODO: test
  def -(subsracted:Fraction) = this + (-subsracted)

  // TODO: test
  def <(that:Price) = (this.bid < that.bid
                         || this.ask < that.ask)

  // TODO: test
  def >(that:Price) = (this.bid > that.bid
                         || this.ask > that.ask)

  // TODO: test
  def min(that:Price) = new Price(this.bid.min(that.bid),
                                  this.ask.min(that.ask))

  // TODO: test
  def max(that:Price) = new Price(this.bid.max(that.bid),
                                  this.ask.max(that.ask))


  override def equals(that:Any) = {
    if ( ! that.isInstanceOf[Price]) false
    else {
      val thatPrice = that.asInstanceOf[Price]

      (thatPrice.bid == bid
         && thatPrice.ask == ask)
    }
  }

  override def toString:String = bid.toString + "|" + ask.toString
}
