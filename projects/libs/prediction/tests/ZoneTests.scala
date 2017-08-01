package tests.prediction.zones

import org.scalatest.FlatSpec

import tas.types.{
  Fraction,
  Time,
  TimedTick,
  Price,
  Buy,
  Sell
}

import tas.prediction.zones.Zone

import tas.trading.TradeParameters

class ZoneTests extends FlatSpec {
  behavior of "Zone"

  def price(bid:Int, ack:Int) = new Price(Fraction(bid), Fraction(ack))

  def tick(price:Price) = new TimedTick(Time.now, price)

  it should "calculate up diff as buy price diff" in {
    val first = price(10, 20)
    val second = price(30, 40)

    assert(new Zone(tick(first), tick(second)).upDiff
             === TradeParameters.profit(Buy, 1, Buy.openPrice(first), Buy.closePrice(second), Fraction.ZERO))
  }

  it should "calculate down diff as sell price diff" in {
    val first = price(10, 20)
    val second = price(30, 40)

    assert(new Zone(tick(first), tick(second)).downDiff
             === TradeParameters.profit(Sell, 1, Sell.openPrice(first), Sell.closePrice(second), Fraction.ZERO))
  }
}
