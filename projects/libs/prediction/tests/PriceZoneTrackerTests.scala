package tests.prediction.zones

import org.scalatest.FlatSpec

import tas.prediction.zones.PriceZoneTracker
import tas.prediction.zones.Zone

import tas.types.{
  TimedTick,
  Fraction,
  Price,
  Time,
  Interval
}

class PriceZoneTrackerTests extends FlatSpec {
  behavior of "price zone tracker"

  class Test {
    var tt:TimedTick = null
    var time = Time.milliseconds(0)
    def tick(bid:Int, ack:Int) = {
      time = time + Interval.milliseconds(1)

      tt = new TimedTick(time, new Price(bid, ack))
      tt
    }

    def getTT() = tt

    val pzt = new PriceZoneTracker(getTT)
  }

  it should "collect zones" in new Test {
    val tt1 = tick(1, 2)

    val zone1 = pzt.enter()

    val tt2 = tick(1, 2)

    zone1.leave()

    val results = pzt.results()

    assert(results._1.zones === results._2.zones)
    assert(results._1.zones === Array(new Zone(tt1, tt2)))
  }

  it should "collect zones ordered by start" in new Test {
    val tt1 = tick(1, 2)

    val zone1 = pzt.enter()

    val tt2 = tick(2, 3)

    val zone2 = pzt.enter()

    val tt3 = tick(3, 4)

    zone2.leave

    zone1.leave()

    val results = pzt.results()

    assert(results._1.zones === results._2.zones)
    assert(results._1.zones === Array(new Zone(tt1, tt3),
                                      new Zone(tt2, tt3)))

    assert(results._1.change === Fraction(1))
    assert(results._2.change === Fraction(-5))
  }



}
