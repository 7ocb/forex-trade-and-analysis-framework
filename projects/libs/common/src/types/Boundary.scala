package tas.types

import tas.output.format.Formatting

object Boundary {

  class Cmp private[Boundary] (val isEqual:Boolean, below:Boolean) {
    val isBelow = below
    val isAbove = ! below

    def withEq = new Cmp(true, isBelow)
    def inverted = new Cmp(!isEqual, isAbove)

    def comparator:(Fraction,Fraction)=>Boolean = {
      if (isBelow) {
        if (isEqual) (a, b) => a <= b
        else         (a, b) => a < b
      } else {
        if (isEqual) (a, b) => a >= b
        else         (a, b) => a > b
      }
    }
  }

  val Greater        = new Cmp(false, false)
  val Less           = new Cmp(false, true)
  val GreaterOrEqual = new Cmp(true, false)
  val LessOrEqual    = new Cmp(true, true)
  
  def >(price:Fraction)  = new Boundary(Greater,        price)
  def <(price:Fraction)  = new Boundary(Less,           price)
  def >=(price:Fraction) = new Boundary(GreaterOrEqual, price)
  def <=(price:Fraction) = new Boundary(LessOrEqual,    price)

} 

class Boundary private (val cmp:Boundary.Cmp,
                        val value:Fraction) {

  def isCrossed(price:Fraction):Boolean = cmp.comparator(price, value)

  def intersects(other:Boundary):Boolean = {
    val sameDirection = cmp.isBelow == other.cmp.isBelow

    // if boundares have same direction, they have point, which can cross both
    if (sameDirection) return true

    val sameValue = value == other.value
    val bothCrossedIfEqual = cmp.isEqual && other.cmp.isEqual
    
    if (sameValue) bothCrossedIfEqual
    else isCrossed(other.value)
  }

  def includes(other:Boundary):Boolean = {
    val sameDirection = cmp.isBelow == other.cmp.isBelow

    if (!sameDirection) return false

    if (cmp.withEq.comparator(other.value, value)) {
      if (other.cmp.isEqual) return cmp.isEqual
      else return true
    } else return false
  }

  def firstCrossingWillAlsoCross(other:Boundary):Boolean = {

    val sameDirection = cmp.isBelow == other.cmp.isBelow

    if (sameDirection) return other.includes(this)
    else return other.intersects(this)

  }

  def inverted = new Boundary(cmp.inverted, value)

  override def equals(obj:Any) = {
    if (obj.isInstanceOf[Boundary]) {
      val a = obj.asInstanceOf[Boundary]

      a.value == this.value && a.cmp.isAbove == this.cmp.isAbove && a.cmp.isEqual == this.cmp.isEqual
    } else false
  }

  override def toString = {
    val direction = if (cmp.isAbove) { ">" } else { "<" }
    val equality = (if (cmp.isEqual) { "= " } else { " " }) + Formatting.format(value)
    "price " + direction + equality
  } 
} 

