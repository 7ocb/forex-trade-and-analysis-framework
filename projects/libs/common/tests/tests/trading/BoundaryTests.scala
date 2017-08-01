package tests.trading

import org.scalatest.FlatSpec

import tas.types.{
  Fraction,
  Boundary
}

class BoundaryTests extends FlatSpec {
  behavior of "Boundary"


  def testValueCross(boundary:Boundary, value:Fraction, expected:Boolean) = {
    it should ("be "
                 +
                 ( if (expected) ""
                   else "not " )
                 +
                 "crossed if boundary: " + boundary + " with value " + value) in {
      assert(boundary.isCrossed(value) === expected)
    }
  }

  testValueCross(Boundary > 1, 1, false)
  testValueCross(Boundary >= 1, 1, true)
  testValueCross(Boundary > 2, 1, false)
  testValueCross(Boundary >= 2, 1, false)
  testValueCross(Boundary > 1, 2, true)
  testValueCross(Boundary >= 1, 2, true)

  testValueCross(Boundary < 1, 1, false)
  testValueCross(Boundary <= 1, 1, true)
  testValueCross(Boundary < 2, 1, true)
  testValueCross(Boundary <= 2, 1, true)
  testValueCross(Boundary < 1, 2, false)
  testValueCross(Boundary <= 1, 2, false)

  def testIntersection(left:Boundary, right:Boundary, expected:Boolean) = {
    it should (( if (expected) ""
                 else "not " )

      + "be crossed at same time: " + left + " with " + right) in {
      val result = left.intersects(right)
      assert(result === right.intersects(left),
             "Non symmetric can be crossed at same time calculation")

      assert(result === expected)
    }
  }

  testIntersection(Boundary < 1, Boundary > 1, false)
  testIntersection(Boundary < 1, Boundary < 2, true)
  testIntersection(Boundary < 1, Boundary >= 1, false)
  testIntersection(Boundary <= 1, Boundary >= 1, true)
  testIntersection(Boundary < 2, Boundary > 1, true)

  def testInversion(original:Boundary, expected:Boundary) = {
    it should ("invert " + original + " to " + expected) in {
      assert(original.inverted === expected)
    }
  }

  testInversion(Boundary < 1, Boundary >= 1)
  testInversion(Boundary > 1, Boundary <= 1)
  testInversion(Boundary >= 1, Boundary < 1)
  testInversion(Boundary <= 1, Boundary > 1)

  def not(notFlag:Boolean) = {
     if (notFlag) "not "
     else "" 
  }


  def testInclusion(first:Boundary, second:Boundary, expected:Boolean) = {
    it should ("show first " + first + " "
                 + not(!expected)
                 + "include " + second) in {
      assert(first.includes(second) === expected)
    }
  }

  testInclusion(Boundary > 3, Boundary > 2,  false)
  testInclusion(Boundary > 2, Boundary > 3,  true)
  testInclusion(Boundary > 2, Boundary < 3,  false)
  testInclusion(Boundary > 2, Boundary > 2,  true)
  testInclusion(Boundary > 2, Boundary >= 2, false)
  testInclusion(Boundary >= 2, Boundary > 2, true)



  def testFirstCrossingWillAlsoCross(first:Boundary, second:Boundary, expected:Boolean) = {
    it should ("show first value of " + first
                 + " "+ not(!expected)
                 + "cross " + second) in {
      assert(first.firstCrossingWillAlsoCross(second) === expected)
    }
  }

  testFirstCrossingWillAlsoCross(Boundary > 2, Boundary > 1, true)
  testFirstCrossingWillAlsoCross(Boundary > 2, Boundary > 3, false)
  testFirstCrossingWillAlsoCross(Boundary < 2, Boundary > 3, false)
  testFirstCrossingWillAlsoCross(Boundary >= 2, Boundary > 2, false)
  testFirstCrossingWillAlsoCross(Boundary > 2, Boundary >= 2, true)
  testFirstCrossingWillAlsoCross(Boundary > 2, Boundary > 2, true)

  testFirstCrossingWillAlsoCross(Boundary > 2, Boundary < 2, false)
  testFirstCrossingWillAlsoCross(Boundary > 2, Boundary < 3, true)
  testFirstCrossingWillAlsoCross(Boundary > 2, Boundary <= 2, false)
  testFirstCrossingWillAlsoCross(Boundary >= 2, Boundary <= 2, true)
}
