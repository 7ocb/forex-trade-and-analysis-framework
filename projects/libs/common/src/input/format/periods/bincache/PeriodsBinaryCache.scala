package tas.input.format.periods.bincache

import tas.types.Fraction.IO._

import tas.types.{
  PeriodBid,
  Time
}

import java.io.{
  InputStream,
  ObjectInputStream,
  OutputStream,
  ObjectOutputStream
}

import tas.input.format.{
  Reader,
  StorageFormat,
  Writer
}

private class PeriodsBinaryCacheReader(stream:InputStream) extends Reader[PeriodBid] {
  private val _input = new ObjectInputStream(stream)

  def read():Option[PeriodBid] = {

    try {
      val priceOpen = _input.readFraction()
      val priceClose = _input.readFraction()
      val priceMin = _input.readFraction()
      val priceMax = _input.readFraction()

      val underlyingMilliseconds = _input.readLong()

      Some(new PeriodBid(priceOpen, priceClose, priceMin, priceMax, Time.milliseconds(underlyingMilliseconds)))
    } catch {
      case eof:java.io.EOFException => None
    }
  }

  def close() = _input.close()
}

private class PeriodsBinaryCacheWriter(stream:OutputStream) extends Writer[PeriodBid] {

  private val _output = new ObjectOutputStream(stream)

  def write(period:PeriodBid) = {
    _output.writeFraction(period.bidOpen)
    _output.writeFraction(period.bidClose)
    _output.writeFraction(period.bidMin)
    _output.writeFraction(period.bidMax)

    _output.writeLong((period.time.underlyingMilliseconds / 1000) * 1000)
  }

  def close() = _output.close()
}


object PeriodsBinaryCache extends StorageFormat[PeriodBid] {
  val name:String = "periods.bin.cache.v3"

  def reader(stream:InputStream):Reader[PeriodBid] = new PeriodsBinaryCacheReader(stream)

  def writer(stream:OutputStream):Writer[PeriodBid] = new PeriodsBinaryCacheWriter(stream)
}
