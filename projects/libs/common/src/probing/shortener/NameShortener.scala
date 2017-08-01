package tas.probing.shortener

import scala.annotation.tailrec

object NameShortener {
  sealed trait ShorteningMark {
    private [NameShortener] def result(forLevel:Int):String
    private [NameShortener] def maxLevel:Int
  }

  case class Drop(unshortened:String,
                  onLevel:Int) extends ShorteningMark {

    override def result(forLevel:Int) = if (forLevel < onLevel) unshortened
                                        else ""

    override def maxLevel = onLevel

    override def toString = "drop " + unshortened + " on " + onLevel
  }

  case class Exact (unshortened:String) extends ShorteningMark {
    override def result(forLevel:Int) = unshortened
    override def maxLevel = 0

    override def toString = "exact " + unshortened + ""
  }

  case class Alternative (unshortened:String,
                          shortened:String,
                          changeOnLevel:Int) extends ShorteningMark {

    override def result(forLevel:Int) = if (forLevel < changeOnLevel) unshortened
                                        else shortened

    override def maxLevel = changeOnLevel

    override def toString = "alt " + unshortened + "->" + shortened + " on " + changeOnLevel
  }

  def apply(markedForShortening:List[ShorteningMark], maxLength:Int):String = {

    val maxLevel = markedForShortening.map(_.maxLevel).max

    @tailrec def shorten(level:Int):String = {
      val shortened = markedForShortening.map(_.result(level)).reduce(_ + _)

      val accept = shortened.length <= maxLength || level == maxLevel

      if (accept) shortened
      else shorten(level + 1)
    }

    val startLevel = 0
    shorten(startLevel)

  }
}
