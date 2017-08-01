package tas.utils.files.naming

import tas.utils.format.StringComplementor

import tas.types.{
  Time,
  Interval
}

sealed trait SourceDataType
class Periods(interval:Interval) extends SourceDataType {
  override def toString = "periods-" + interval.toStringShortForm
}

object Ticks extends SourceDataType {
  override def toString = "ticks"
}

sealed trait SourceSpec {
  def broker:String
  def extra:Option[String]
}

class TicksFromPeriods(val broker:String, val seed:String) extends SourceSpec {
  override def extra = Some("from-periods-" + seed)
}

class FromBroker(val broker:String) extends SourceSpec {
  def extra:Option[String] = None
}


object SourceFileName {

  private val dayMonthComplemented = new StringComplementor(2, "0")
  private val yearComplemented = new StringComplementor(4, "0")

  private def asDate(t:Time) = (yearComplemented(t.year.toString)
                                  + "-" + dayMonthComplemented(t.month.toString)
                                  + "-" + dayMonthComplemented(t.day.toString))

  def apply(pairCode:String,
            dataType:SourceDataType,
            start:Time,
            end:Time,
            source:SourceSpec):String = {

    val extra = source.extra.map { "-" + _ } getOrElse("")

    source.broker + "-" + pairCode + "-" + dataType + "-" + asDate(start) + "---" + asDate(end) + extra + ".txt"
  }

}
