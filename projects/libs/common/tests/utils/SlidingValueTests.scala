package tests.utils

import tas.utils.SlidingValue

import org.scalatest.FlatSpec

class SlidingValueTests extends FlatSpec {
  behavior of "sliding value tests"

  val zeroList = List[Int]()
  def foldToList(a:List[Int], v:Int) = a ++ List(v)

  it should "throw exception until filled" in {
    val ts = new SlidingValue[Int](10)



    for (a <- 1 to 10) {
      assert(ts.isFilled === false)

      intercept[RuntimeException] {
        ts.foldL(zeroList, foldToList)
      }

      ts += a
    }

    assert(ts.isFilled === true)
    assert(ts.foldL(zeroList, foldToList) === (1 to 10))
  }

  it should "correctly overfill" in {
    val ts = new SlidingValue[Int](10)

    for (a <- 1 to 25) {
      ts += a 
    }

    assert(ts.isFilled === true)
    assert(ts.foldL(zeroList, foldToList) === (16 to 25))
  }

  it should "correctly convert self to list" in {
    val ts = new SlidingValue[Int](10)

    for (a <- 1 to 25) {
      ts += a
    }

    assert(ts.isFilled === true)

    assert(ts.foldL(zeroList, foldToList) === ts.toList)
  }

}
