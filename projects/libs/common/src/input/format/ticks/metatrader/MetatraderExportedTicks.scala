package tas.input.format.ticks.metatrader

import tas.input.format.{
  StorageFormat,
  Reader,
  Writer
}

import java.io.{
  InputStream,
  OutputStream,
  PrintWriter,
  OutputStreamWriter
}

import tas.types.{
  TimedBid,
  Time,
  Fraction
}

private class MetatraderExportedTicksReader(stream:java.io.InputStream) extends Reader[TimedBid] {
  val _reader = new java.io.BufferedReader(new java.io.InputStreamReader(stream))

  def read():Option[TimedBid] = {

    val nextString = _reader.readLine

    if (nextString == null) return None

    return MetatraderExportedTicks.parseLine(nextString)
  }

  def close() = _reader.close
}

private class MetatraderExportedTicksWriter(stream:OutputStream) extends Writer[TimedBid] {

  private val _output = new PrintWriter(new OutputStreamWriter(stream))

  def write(tick:TimedBid) = {
    val time = tick.time
    val outLine = ("%04d-%02d-%02d-%02d-%02d-%02d ".format(time.year, time.month, time.day, time.hours, time.minutes, time.seconds)
                     + tick.bid)

    _output.println(outLine)
  }

  def close() = _output.close()
}

object MetatraderExportedTicks extends StorageFormat[TimedBid] {

  val name:String = "ticks.mt.txt"

  def parseLine(line:String):Option[TimedBid] = {
    val dateAndPrice = line.split(" ")

    if (dateAndPrice.size != 2) return None

    val dateString = dateAndPrice(0)
    val priceString = dateAndPrice(1)

    val dateElements = dateString.split("-")

    if (dateElements.size != 6) return None

    def date(index:Int) = dateElements(index).toInt

    try {
      Some(new TimedBid(Time.fromCalendar(date(0),
                                           date(1),
                                           date(2),
                                           date(3),
                                           date(4),
                                           date(5)),
                         Fraction(priceString)))
    } catch {
      case nfe:NumberFormatException => None
    }
  }

  def reader(stream:InputStream):Reader[TimedBid] = new MetatraderExportedTicksReader(stream)

  def writer(stream:OutputStream):Writer[TimedBid] = new MetatraderExportedTicksWriter(stream)
}
