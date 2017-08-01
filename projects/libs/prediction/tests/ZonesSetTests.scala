package tests.prediction.zones

import org.scalatest.FlatSpec

import tas.prediction.zones.{
  Zone,
  ZonesSet
}

import tas.types.{
  Price,
  TimedTick,
  Fraction,
  Time
}

class ZonesSetTests extends FlatSpec {
  behavior of "zones set"

  def f(fr:Int) = Fraction(fr)

  def price(bid:Int, ack:Int) = new Price(Fraction(bid), Fraction(ack))

  def tick(ms:Int, price:Price) = new TimedTick(Time.milliseconds(ms),
                                                price)

  it should "calculate count" in {
    val zs = ZonesSet(List(new Zone(tick(0, price(1,2)),
                                    tick(1, price(2,3))),
                           new Zone(tick(0, price(1,2)),
                                    tick(2, price(2,3))),
                           new Zone(tick(1, price(1,2)),
                                    tick(2, price(2,3)))),
                      "n/a",
                      _.upDiff)
    assert(zs.count === 3)
  }

  it should "calculate change using upDiff" in {
    val zs = ZonesSet(List(new Zone(tick(0, price(1,2)),
                                    tick(1, price(2,3))),
                           new Zone(tick(0, price(1,2)),
                                    tick(2, price(3,4))),
                           new Zone(tick(1, price(1,2)),
                                    tick(2, price(3,4)))),
                      "n/a",
                      _.upDiff)

    assert(zs.change === Fraction(2))
  }

  it should "calculate change using downDiff" in {
    val zs = ZonesSet(List(new Zone(tick(0, price(1,2)),
                                    tick(1, price(2,3))),
                           new Zone(tick(0, price(1,2)),
                                    tick(2, price(3,4))),
                           new Zone(tick(1, price(1,2)),
                                    tick(2, price(3,4)))),
                      "n/a",
                      _.downDiff)

    assert(zs.change === Fraction(-8))
  }

  it should "get first" in {
    val zs = ZonesSet(List(new Zone(tick(0, price(1,2)),
                                    tick(1, price(2,3))),
                           new Zone(tick(0, price(1,2)),
                                    tick(2, price(3,4))),
                           new Zone(tick(1, price(1,2)),
                                    tick(2, price(3,4)))),
                      "n/a",
                      _.downDiff)

    assert(zs.first === new Zone(tick(0, price(1,2)),
                                 tick(1, price(2,3))))
  }

  it should "get last" in {
    val zs = ZonesSet(List(new Zone(tick(0, price(1,2)),
                                    tick(1, price(2,3))),
                           new Zone(tick(0, price(1,2)),
                                    tick(2, price(3,4))),
                           new Zone(tick(1, price(1,2)),
                                    tick(2, price(3,4)))),
                      "n/a",
                      _.downDiff)

    assert(zs.last === new Zone(tick(1, price(1,2)),
                                tick(2, price(3,4))))
  }

  it should "get isEmpty" in {

    assert(ZonesSet(List(new Zone(tick(0, price(1,2)),
                                  tick(1, price(2,3)))),
                    "n/a",
                    _.downDiff).isEmpty === false)

    assert(ZonesSet(List[Zone](),
                    "n/a",
                    _.downDiff).isEmpty === true)
  }

  it should "calculate changes by the end time" in {
    val zs = ZonesSet(List(new Zone(tick(0, price(1,2)),
                                    tick(1, price(2,3))),
                           new Zone(tick(0, price(1,2)),
                                    tick(2, price(3,4))),
                           new Zone(tick(1, price(1,2)),
                                    tick(2, price(3,5)))),
                      "n/a",
                      _.downDiff)

    assert(zs.changes === Array(f(-2), f(-3), f(-4)))

    val zs2 = ZonesSet(List(new Zone(tick(0, price(1,2)),
                                     tick(1, price(2,3))),
                            new Zone(tick(0, price(1,2)),
                                     tick(3, price(3,4))),
                            new Zone(tick(1, price(1,2)),
                                     tick(2, price(3,6))),
                            new Zone(tick(2, price(1,2)),
                                     tick(3, price(3,5)))),
                       "n/a",
                       _.downDiff)

    assert(zs2.changes === Array(f(-2), f(-5), f(-3), f(-4)))
  }

  it should "calculate way by the end time" in {
    val zs = ZonesSet(List(new Zone(tick(0, price(1,2)),
                                    tick(1, price(2,3))),
                           new Zone(tick(0, price(1,2)),
                                    tick(2, price(3,4))),
                           new Zone(tick(1, price(1,2)),
                                    tick(2, price(3,5)))),
                      "n/a",
                      _.downDiff)

    assert(zs.way === Array(f(0), f(-2), f(-5), f(-9)))

    val zs2 = ZonesSet(List(new Zone(tick(0, price(1,2)),
                                     tick(1, price(2,3))),
                            new Zone(tick(0, price(1,2)),
                                     tick(3, price(3,4))),
                            new Zone(tick(1, price(1,2)),
                                     tick(2, price(3,6))),
                            new Zone(tick(2, price(1,2)),
                                     tick(3, price(3,5)))),
                       "n/a",
                       _.downDiff)

    assert(zs2.way === Array(f(0), f(-2), f(-7), f(-10), f(-14)))
  }

  it should "calculate sections" in {
    val zs2 = ZonesSet(List(new Zone(tick(0, price(1,2)),
                                     tick(10, price(2,3))),
                            new Zone(tick(0, price(1,2)),
                                     tick(30, price(3,4))),
                            new Zone(tick(5, price(1,2)),
                                     tick(10, price(3,6))),
                            new Zone(tick(5, price(1,2)),
                                     tick(20, price(3,6))),
                            new Zone(tick(10, price(1,2)),
                                     tick(20, price(3,6))),
                            new Zone(tick(20, price(1,2)),
                                     tick(30, price(3,5)))),
                       "n/a",
                       _.downDiff)

    assert(zs2.sections(2).map(_.zones.toList) === List(List(new Zone(tick(0, price(1,2)),
                                                                      tick(10, price(2,3))),
                                                             new Zone(tick(5, price(1,2)),
                                                                      tick(10, price(3,6)))),
                                                        List(new Zone(tick(0, price(1,2)),
                                                                      tick(30, price(3,4))),
                                                             new Zone(tick(5, price(1,2)),
                                                                      tick(20, price(3,6))),
                                                             new Zone(tick(10, price(1,2)),
                                                                      tick(20, price(3,6))),
                                                             new Zone(tick(20, price(1,2)),
                                                                      tick(30, price(3,5))))))
  }

  it should "calculate footprint" in {

    assert(ZonesSet(List(new Zone(tick(0, price(1,2)),
                                  tick(10, price(2,3))),
                         new Zone(tick(0, price(1,2)),
                                  tick(30, price(3,4))),
                         new Zone(tick(5, price(1,2)),
                                  tick(10, price(3,6))),
                         new Zone(tick(5, price(1,2)),
                                  tick(20, price(3,6))),
                         new Zone(tick(10, price(1,2)),
                                  tick(20, price(3,6))),
                         new Zone(tick(20, price(1,2)),
                                  tick(30, price(3,5)))),
                    "n/a",
                    _.downDiff).footprint(2).boolKey
             === List(false, false))

    assert(ZonesSet(List(new Zone(tick(0, price(1,2)),
                                  tick(10, price(2,3))),
                         new Zone(tick(0, price(1,2)),
                                  tick(30, price(2,4))),
                         new Zone(tick(5, price(1,2)),
                                  tick(10, price(3,6))),
                         new Zone(tick(5, price(1,2)),
                                  tick(20, price(3,6))),
                         new Zone(tick(10, price(1,2)),
                                  tick(20, price(3,6))),
                         new Zone(tick(20, price(1,2)),
                                  tick(30, price(3,5)))),
                    "n/a",
                    _.upDiff).footprint(2).boolKey
             === List(true, true))

    assert(ZonesSet(List(new Zone(tick(0, price(1,2)),
                                  tick(10, price(2,3))),
                         new Zone(tick(0, price(1,3)),
                                  tick(30, price(2,4))),
                         new Zone(tick(5, price(1,2)),
                                  tick(10, price(2,6))),
                         new Zone(tick(5, price(1,2)),
                                  tick(20, price(3,6))),
                         new Zone(tick(10, price(1,2)),
                                  tick(20, price(3,6))),
                         new Zone(tick(20, price(1,2)),
                                  tick(30, price(3,5)))),
                    "n/a",
                    _.upDiff).footprint(2).boolKey
             === List(false, true))
  }

  it should "calculate rating" in {
    assert(ZonesSet(List(new Zone(tick(0, price(1,2)),
                                  tick(30, price(2,4)))),
                    "n/a",
                    _.upDiff).rating
             === 1)

    assert(ZonesSet(List(new Zone(tick(0, price(1,2)),
                                  tick(30, price(2,4)))),
                    "n/a",
                    _.downDiff).rating
             === 0)

    assert(ZonesSet(List(new Zone(tick(0, price(1,2)),
                                  tick(10, price(2,3))),
                         new Zone(tick(0, price(1,2)),
                                  tick(30, price(2,4))),
                         new Zone(tick(5, price(1,2)),
                                  tick(10, price(3,6))),
                         new Zone(tick(5, price(1,2)),
                                  tick(20, price(3,6))),
                         new Zone(tick(10, price(1,2)),
                                  tick(20, price(3,6))),
                         new Zone(tick(20, price(1,2)),
                                  tick(30, price(3,5)))),
                    "n/a",
                    _.upDiff).rating
             === 2)
  }

}
