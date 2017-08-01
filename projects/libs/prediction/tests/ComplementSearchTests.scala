package tests.prediction.zones

import org.scalatest.FlatSpec

import tas.prediction.zones.{
  Zone,
  ZonesSet,
  ComplementSearch
}

import tas.types.{
  Price,
  TimedTick,
  Fraction,
  Time,
  Interval
}

class ComplementSearchTests extends FlatSpec {
  
  val random = new scala.util.Random

  def z(changes:List[Int]) = {
    val count = changes.size

    val zeroTime = Time.milliseconds(100)
    val interval = Interval.milliseconds(100)

    val set = new ZonesSet(Array.tabulate(count)(i => new Zone(new TimedTick(zeroTime + (interval*(i*2 + 1)),
                                                                             Price(Fraction(0), Fraction(0))),
                                                               new TimedTick(zeroTime + (interval*(i*2 + 2)),
                                                                             Price(Fraction(changes(i)), Fraction(changes(i)))))),
                           "n/a",
                           _.upDiff)

    assert(set.footprint(count).boolKey === changes.map(_ > 0))

    set
  }

  def complements(level:Int, zones:List[ZonesSet]) = {
    new ComplementSearch(level,
                         zones.map(new ComplementSearch.Qualified(_, 0)))
      .complements.map(a => toZonesList((a._1.zones, a._2.zones)))
  }

  def toZonesList(a:(ZonesSet, ZonesSet)) = (a._1.zones.toList, a._2.zones.toList)

  class Comparable[T](val list:List[(T, T)]) {
    override def equals(o:Any) = o match {
        case that:Comparable[T] => isSamePairs(that.list, list)
        case _ => false
      }

    override def toString = list.toString

    def isSamePairs(left:List[(T, T)], right:List[(T, T)]):Boolean =
      if (left.isEmpty && right.isEmpty) true
      else if (left.isEmpty || right.isEmpty) false
      else {
        val item = left.head
        val alternative = (item._2, item._1)

        def continueForItem(i:(T,T)):Boolean = {
          val index = right.indexOf(i)
          if (index < 0) false
          else isSamePairs(left.tail, right.take(index) ++ right.drop(index + 1))
        }

        continueForItem(item) || continueForItem(alternative)
      }

    def isEmpty = list.isEmpty
  }

  def comparable[T](list:List[(T, T)]) = new Comparable(list)

  def zonesPairs(list:List[(ZonesSet,ZonesSet)]) = comparable(list.map(toZonesList))

  behavior of "complement search "
  
  it should "search complements simple case" in {
    val a1 = z(List(2,-1))
    val a2 = z(List(-1,2))

    assert(comparable(complements(2, List(a1, a2)))
             === comparable(List((a1, a2)).map(toZonesList)))

  }

  it should "don't report complement for complement case when result is zero" in {
    val a1 = z(List(1,-1))
    val a2 = z(List(-1,1))

    assert(comparable(complements(2, List(a1, a2))).isEmpty)
  }

  it should "seach complements more cases" in {
    val a1 = z(List(2,-1,-1)) // a2
    val a2 = z(List(-1,2,2))  // a1
    val a3 = z(List(1,2,-1))  // nothing
    val a4 = z(List(-1,1,-1)) // nothing
    val a5 = z(List(-1,-2,5)) // nothing

    assert(comparable(complements(3, List(a1, a2, a3, a4, a5)))
             === zonesPairs(List((a1, a2))))
  }
}
