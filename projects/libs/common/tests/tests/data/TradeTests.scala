package tests.data

import org.scalatest.FlatSpec

import tas.types.{
  Trade, Sell, Buy,
  Price,
  Boundary
}

class TradeTests extends FlatSpec {
  behavior of "TradeTests"

  it should "return ask as Buy open price" in {
    val price = new Price("0.0001", "0.0002")
    assert(Buy.openPrice(price) === price.ask)
  }

  it should "return ask as Sell close price" in {
    val price = new Price("0.0001", "0.0002")
    assert(Sell.closePrice(price) === price.ask)
  }

  it should "return bid as Buy close price" in {
    val price = new Price("0.0001", "0.0002")
    assert(Buy.closePrice(price) === price.bid)
  }

  it should "return bid as Sell open price" in {
    val price = new Price("0.0001", "0.0002")
    assert(Sell.openPrice(price) === price.bid)
  }

  it should "calculate tp in different direction than delay" in {
    val price = new Price("0.0010", "0.0012")

    def differentDirection(one:Boundary, two:Boundary) = assert(one.cmp.isBelow != two.cmp.isBelow)

    differentDirection(Sell.takeProfit("0.0001"),
                       Sell.delay("0.0001"))

    differentDirection(Sell.takeProfit("0.0001", "0"),
                       Sell.delay("0.0001", "0"))

  }

  it should "calculate tp for Buy in up direction" in {
    assert(Buy.takeProfit("1") === (Boundary >= "1"))
    assert(Buy.takeProfit("1", "0.1") === (Boundary >= "1.1"))
  }

  it should "calculate stop for Buy in down direction" in {
    assert(Buy.stop("1") === (Boundary <= "1"))
    assert(Buy.stop("1", "0.1") === (Boundary <= "0.9"))
  }

  it should "calculate delay for Buy in down direction" in {
    assert(Buy.delay("1") === (Boundary <= "1"))
    assert(Buy.delay("1", "0.1") === (Boundary <= "0.9"))
    info("from ask")
    assert(Buy.delay(new Price("1", "2"), "0.1") === (Boundary <= "1.9"))
  }

  it should "calculate tp for Sell in down direction" in {
    assert(Sell.takeProfit("1") === (Boundary <= "1"))
    assert(Sell.takeProfit("1", "0.1") === (Boundary <= "0.9"))
  }

  it should "calculate stop for Sell in up direction" in {
    assert(Sell.stop("1") === (Boundary >= "1"))
    assert(Sell.stop("1", "0.1") === (Boundary >= "1.1"))
  }

  it should "calculate delay for Sell in up direction" in {
    assert(Sell.delay("1") === (Boundary >= "1"))
    assert(Sell.delay("1", "0.1") === (Boundary >= "1.1"))
    info("from bid")
    assert(Sell.delay(new Price("1", "2"), "0.1") === (Boundary >= "1.1"))
  }
}
