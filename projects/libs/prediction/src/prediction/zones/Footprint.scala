package tas.prediction.zones

import tas.types.Fraction

object Footprint {

  def etalonFor(level:Int) = foldToIntKey(List.fill(level)(true))

  def foldToIntKey(l:List[Boolean]):Int =
    l.foldLeft(0)((a:Int, v:Boolean) => a << 1 | (if (v) 1
                                                  else 0))
}

final class Footprint(val steps:List[Fraction]) {
  lazy val change = steps.sum
  lazy val count = steps.size

  lazy val boolKey = steps.map(_ > 0)

  lazy val failsCount = boolKey.count(!_)
  lazy val successCount = count - failsCount
  lazy val haveFails = boolKey.contains(false)

  lazy val intKey = if (count > 32) throw new RuntimeException("Supported only for levels <= 32")
                    else Footprint.foldToIntKey(boolKey)

  def +(other:Footprint) = new Footprint(steps
                                           .zip(other.steps)
                                           .map(a => (a._1 + a._2)))

  override def toString = steps.toString
  override def equals(o:Any) =
    o.isInstanceOf[Footprint] && o.asInstanceOf[Footprint].steps == steps

}
