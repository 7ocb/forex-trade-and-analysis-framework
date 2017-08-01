package tas.prediction.zones

import java.io.Serializable

import scala.collection.mutable.HashMap

import tas.types.{
  Fraction,
  Time
}

object ZonesSet {
  private class ListAccess(input:Array[Zone], sortBy:(Zone=>Time)) {
    lazy val data = input.sortBy(sortBy)
    lazy val first = data(0)
    lazy val last  = data(input.length - 1)
  }

  def apply(zones:List[Zone],
            direction:String,
            diffGetter:(Zone=>Fraction)) = new ZonesSet(zones.toArray, direction, diffGetter)

  final case class Timed[T](time:Time, value:T)

  def wayFromChanges(changes:Array[Fraction]) = changes.foldLeft(List(Fraction.ZERO))((a, c) => { (a.head + c) :: a } ).reverse.toArray
}

class ZonesSet(val zones:Array[Zone],
               val direction:String,
               diffGetter:(Zone=>Fraction)) extends Serializable {

  import ZonesSet.{
    ListAccess,
    Timed,
    wayFromChanges
  }

  @transient
  private lazy val sortedByStartTime = new ListAccess(zones, _.start.time)

  @transient
  private lazy val sortedByEndTime   = new ListAccess(zones, _.end.time)

  @transient
  lazy val count = zones.size

  def timedChanges = sortedByEndTime.data.map(a => Timed(a.end.time,
                                                         diffGetter(a)))

  @transient
  lazy val changes = sortedByEndTime.data.map(diffGetter)

  @transient
  lazy val change = changes.sum

  @transient
  lazy val first = sortedByStartTime.first

  @transient
  lazy val last = sortedByEndTime.last

  @transient
  lazy val way = wayFromChanges(changes)

  @transient
  val isEmpty = zones.isEmpty

  def sections(count:Int):List[ZonesSet] = {
    if (isEmpty) return List.fill(count)(ZonesSet(List[Zone](), direction, diffGetter))

    val startTime     = zones.head.start.time
    val endTime       = sortedByEndTime.last.end.time
    val wholeInterval = endTime - startTime
    val stepInterval  = wholeInterval / count

    val maxGroup = count - 1

    val groups = zones.groupBy(z => maxGroup.min((z.end.time - startTime) / stepInterval))

    {
      for (index <- 0 to (count - 1)) yield new ZonesSet(groups.getOrElse(index,
                                                                          Array[Zone]()),
                                                         direction,
                                                         diffGetter)
    }.toList
  }

  def footprint(sectionsCount:Int):Footprint = 
    new Footprint(sections(sectionsCount).map(_.change).toList)
  

  @transient
  lazy val rating = if (change < 0) 0
                    else rate(2)

  private def rate(n:Int):Int =
    if (footprint(n).haveFails) n - 1
    else rate(n + 1)

  override def toString = zones.toList.toString

}
