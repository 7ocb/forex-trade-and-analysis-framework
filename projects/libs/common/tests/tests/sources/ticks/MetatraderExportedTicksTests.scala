package tests.sources.ticks

import org.scalatest.FlatSpec

import tas.input.format.ticks.metatrader.MetatraderExportedTicks

import tests.readers.StorageFormatTests

import tas.types.{
  TimedBid,
  Time,
  Fraction
}

class MetatraderExportedTicksTests extends StorageFormatTests {
  behavior of "MetatraderExportedTicks"

  readWriteTestTicks(MetatraderExportedTicks)

  def testParsing(string:String, expected:Option[TimedBid]) = {
    it should ("parse " + expected + " from " + string) in {
      assert(MetatraderExportedTicks.parseLine(string) === expected)
    }
  }

  testParsing("2013-10-11-12-13-14 1.23",
              Some(new TimedBid(Time.fromCalendar(2013, 10, 11, 12, 13, 14),
                                Fraction("1.23"))))

  testParsing("-10-11-12-13-14 1.23",
              None)
  testParsing("----- 1.23",
              None)


  testParsing("2012-asd-11-12-13-14 1.23",
              None)

  testParsing("2012-asd-11-12-13-14 ",
              None)

  testParsing("2012-asd-11-12-13-15",
              None)

  testParsing("asdf asdf",
              None)



}
