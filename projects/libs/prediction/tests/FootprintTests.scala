package tests.prediction.zones

import tas.prediction.zones.Footprint
import tas.types.Fraction

import org.scalatest.FlatSpec

class FootprintTests extends FlatSpec {
  behavior of "Footprint"

  def f(v:Int) = Fraction(v)

  it should "calculate change correctly" in {
    assert(new Footprint(List(f(1), f(2), f(3))).change === f(6))
    assert(new Footprint(List(f(1), f(2), f(-3), f(-2), f(1))).change === f(-1))
  }

  it should "calculate size correclty" in {
    assert(new Footprint(List(f(1), f(0), f(1), f(2))).count === 4)
    assert(new Footprint(List(f(1), f(0), f(1), f(2), f(0), f(3))).count === 6)
  }

  it should "calculate bool keys correctly" in {
    assert(new Footprint(List(f(1), f(0))).boolKey
             === List(true, false))
    assert(new Footprint(List(f(1), f(0), f(-1), f(2), f(3))).boolKey
             === List(true, false, false, true, true))

    assert(new Footprint(List(f(0), f(-1), f(0), f(0))).boolKey
             === List(false, false, false, false))

    assert(new Footprint(List(f(1), f(1), f(1), f(1))).boolKey
             === List(true, true, true, true))
  }

  it should "calculate fails count correclty" in {
    assert(new Footprint(List(f(1), f(0), f(2))).failsCount === 1)
    assert(new Footprint(List(f(-1), f(0), f(2))).failsCount === 2)
    assert(new Footprint(List(f(1), f(1), f(2))).failsCount === 0)
  }

  it should "calculate success count correclty" in {
    assert(new Footprint(List(f(1), f(0), f(2))).successCount === 2)
    assert(new Footprint(List(f(-1), f(0), f(2))).successCount === 1)
    assert(new Footprint(List(f(1), f(1), f(2))).successCount === 3)
    assert(new Footprint(List(f(-1), f(-1), f(-2))).successCount === 0)
  }

  it should "calculate have fails correctly" in {
    assert(new Footprint(List(f(1), f(0), f(2))).haveFails === true)
    assert(new Footprint(List(f(-1), f(0), f(2))).haveFails === true)
    assert(new Footprint(List(f(1), f(1), f(2))).haveFails === false)
  }

  it should "correctly sum" in {
    assert((new Footprint(List(f(1), f(0), f(3), f(-1)))
              + new Footprint(List(f(2), f(-1), f(2), f(1))))
             === new Footprint(List(f(3), f(-1), f(5), f(0))))
  }

  it should "correctly build int key" in {
    assert(new Footprint(List(f(1), f(0))).intKey === 2)
    assert(new Footprint(List(f(-1), f(1))).intKey === 1)
    assert(new Footprint(List(f(1), f(0), f(1))).intKey === 5)
  }

  it should "correctly build etalon" in {
    assert(Footprint.etalonFor(2) === 3)
    assert(Footprint.etalonFor(3) === 7)
    assert(Footprint.etalonFor(4) === 15)
  }

}
