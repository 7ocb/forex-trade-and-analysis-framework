package tas.types

final case class TimedBid(val time:Time,
                          val bid:Fraction) {
  def tick(spread:Fraction) = new TimedTick(time,
                                            Price.fromBid(bid, spread))
}
