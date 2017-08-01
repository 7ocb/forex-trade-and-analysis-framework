package tas.input.format.periods.text

import tas.types.PeriodBid

import java.io.{
  InputStream,
  OutputStream
}

import tas.input.format.{
  Reader,
  StorageFormat,
  Writer
}

object PeriodsText extends StorageFormat[PeriodBid] {
  val name:String = "txt"

  def reader(stream:InputStream):Reader[PeriodBid] = new PeriodsTextReader(stream)

  def writer(stream:OutputStream):Writer[PeriodBid] = new PeriodsTextWriter(stream)
}
