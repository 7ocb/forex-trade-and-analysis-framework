package tas.utils.parsers

import tas.types.Interval

object IntervalParser {

  private lazy val splitRegex = "([0-9]+)([a-z]+)".r
  
  private class ParseSetter(val symbol:String, val createInterval:(Int)=>Interval, val limit:Int = 0)

  private val setters = List(new ParseSetter("d", Interval.days(_)),
                             new ParseSetter("h", Interval.hours(_), 24),
                             new ParseSetter("m", Interval.minutes(_), 60),
                             new ParseSetter("s", Interval.seconds(_), 60),
                             new ParseSetter("ms", Interval.milliseconds(_), 1000))

  private def toInterval(num:String, code:String) = {
    val intNum = try {
      num.toInt
    } catch {
      case e:Throwable => throw new FormatError("Can't parse " + num + code + " to interval: " + num + " is not a integer")
    }

    val setter = setters.find(_.symbol == code)

    if (setter.isEmpty) throw new FormatError("Can't parse " + num + code + " to interval: " + code + " unexpected, expected one of: " + setters.map(_.symbol).mkString(","))

    val limit = setter.get.limit
    
    if (limit > 0 && intNum >= limit) throw new FormatError("Can't parse " + num + code + " to interval: " + num + " too big, maximum is " + limit)
      
    setter.get.createInterval(intNum)
  } 
  
  def apply(str:String):Interval = {
    val intervals = splitRegex.findAllMatchIn(str).map(i => toInterval(i.group(1), i.group(2))).filter(_ != null).toList

    if (intervals.size == 0) throw new FormatError("Can't parse " + str + " to interval")
    
    intervals.reduce(_ + _)
  }

  val sample = "1d2h3m10s100ms"

} 
