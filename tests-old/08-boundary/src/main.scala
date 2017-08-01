import tas.trading.Boundary

object TestMain extends App {
  def close(stopValue:Boundary, price:Double) {
    if (!stopValue.isCrossed(price)) throw new Error("In this case condition should fire stop.")
  }

  def skip(stopValue:Boundary, price:Double) {
    if (stopValue.isCrossed(price)) throw new Error("In this case condition should not fire stop.")
  }

  def check(b:Boolean) = if (!b) throw new Error("Check failed.")

  val below = Boundary < 10

  check(below.cmp.isBelow)
  check(!below.cmp.isAbove)
  check(!below.cmp.isEqual)

  skip(below, 14)
  skip(below, 10)
  close(below, 9)
  close(below, 2)

  val above = Boundary > 10

  check(!above.cmp.isBelow)
  check(above.cmp.isAbove)
  check(!above.cmp.isEqual)

  
  skip(above, 2)
  skip(above, 10)
  close(above, 11)
  close(above, 20)

  val belowOrEq = Boundary <= 10

  check(belowOrEq.cmp.isBelow)
  check(!belowOrEq.cmp.isAbove)
  check(belowOrEq.cmp.isEqual)
  
  skip(belowOrEq, 14)
  close(belowOrEq, 10)
  close(belowOrEq, 2)

  val aboveOrEq = Boundary >= 10

  check(!aboveOrEq.cmp.isBelow)
  check(aboveOrEq.cmp.isAbove)
  check(aboveOrEq.cmp.isEqual)
  
  close(aboveOrEq, 14)
  close(aboveOrEq, 10)
  skip(aboveOrEq, 2)


}   
