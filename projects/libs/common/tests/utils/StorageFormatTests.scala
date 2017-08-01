package tests.readers

import org.scalatest.FlatSpec

import tas.types.{
  PeriodBid,
  Time,
  Fraction,
  TimedBid
}

import tas.input.format.StorageFormat
import tas.input.format.Reader
import tas.input.ReaderSequence

import tas.readers.PeriodsSequence

import java.io.{
  ByteArrayOutputStream,
  ByteArrayInputStream
}

abstract class StorageFormatTests extends FlatSpec {

  def readWriteTestPeriods(format:StorageFormat[PeriodBid]) = {
    it should "read same periods as write" in {

      val testPeriods = List(new PeriodBid(1, 2, 3, 4, Time.milliseconds(1000)),
                             new PeriodBid(Fraction("3.121212"), 2, Fraction("3.121212"), 4, Time.fromCalendar(2012,12,12)),
                             new PeriodBid(0, 0, 0, 0, Time.milliseconds(0)))

      val result = readWriteCycle(testPeriods,
                                  format)

      assert(testPeriods === result)
    }

    it should "null milliseconds on write-read cycle" in {
      // as not all formats support storing of milliseconds, we will assume
      // this as requirement - format must omit milliseconds

      val testData = new PeriodBid(1, 2, 3, 4, Time.milliseconds(1200))
      val expected = new PeriodBid(1, 2, 3, 4, Time.milliseconds(1000))

      assert(readWriteCycle(List(testData),
                            format).head === expected)
    }
  }

  def readWriteTestTicks(format:StorageFormat[TimedBid]) = {
    it should "read same ticks as write" in {

      val testPeriods = List(new TimedBid(Time.milliseconds(1000),
                                           2),
                             new TimedBid(Time.fromCalendar(2012,12,12),
                                           // this will be rounded
                                           Fraction("3.1234567891231456")),
                             new TimedBid(Time.milliseconds(0),
                                           0))

      val result = readWriteCycle(testPeriods,
                                  format)

      assert(testPeriods === result)
    }

    it should "null milliseconds on write-read cycle" in {
      // as not all formats support storing of milliseconds, we will assume
      // this as requirement - format must omit milliseconds

      val testData = new TimedBid(Time.milliseconds(1200), 0)
      val expected = new TimedBid(Time.milliseconds(1000), 0)

      assert(readWriteCycle(List(testData),
                            format).head === expected)
    }
  }

  private def readWriteCycle[T](data:List[T],
                                format:StorageFormat[T]):List[T] = {
    val output = new ByteArrayOutputStream()

    val writer = format.writer(output)

    data.foreach(writer.write)

    writer.close()

    val bytes = output.toByteArray()

    val input = new ByteArrayInputStream(bytes)

    new ReaderSequence(format.reader(input)).all()
  }
} 
