package tas.input.format.ticks.bincache

import tas.types.Fraction.IO._

import tas.types.{
  TimedBid,
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

private class TicksBinaryCacheReader(stream:InputStream) extends Reader[TimedBid] {
  private val _input = new ObjectInputStream(stream)

  def read():Option[TimedBid] = {

    try {
      val price = _input.readFraction()

      val underlyingMilliseconds = _input.readLong()

      Some(new TimedBid(Time.milliseconds(underlyingMilliseconds),
                         price))
    } catch {
      case eof:java.io.EOFException => None
    }
  }

  def close() = _input.close()
}

private class TicksBinaryCacheWriter(stream:OutputStream) extends Writer[TimedBid] {

  private val _output = new ObjectOutputStream(stream)

  def write(timedBid:TimedBid) = {
    _output.writeFraction(timedBid.bid)

    _output.writeLong((timedBid.time.underlyingMilliseconds / 1000) * 1000)
  }

  def close() = _output.close()
}


object TicksBinaryCache extends StorageFormat[TimedBid] {
  val name:String = "ticks.bin.cache.v3"

  def reader(stream:InputStream):Reader[TimedBid] = new TicksBinaryCacheReader(stream)

  def writer(stream:OutputStream):Writer[TimedBid] = new TicksBinaryCacheWriter(stream)
}
