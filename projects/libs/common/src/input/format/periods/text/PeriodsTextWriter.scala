package tas.input.format.periods.text

import java.io.{
  OutputStreamWriter,
  OutputStream,
  PrintWriter
}

import tas.types.PeriodBid

import tas.input.format.{
  Writer
}

private class PeriodsTextWriter(stream:OutputStream) extends Writer[PeriodBid] {

  private val _output = new PrintWriter(new OutputStreamWriter(stream))

  _output.println("<DATE> <TIME> <OPEN> <HIGH> <LOW> <CLOSE>")

  def write(period:PeriodBid) = {
    val time = period.time
    val outLine = ("%04d%02d%02d %02d%02d%02d ".format(time.year, time.month, time.day, time.hours, time.minutes, time.seconds)
                     + period.bidOpen + " "
                     + period.bidMax + " "
                     + period.bidMin + " "
                     + period.bidClose)

    _output.println(outLine)
  }

  def close() = _output.close()
}
