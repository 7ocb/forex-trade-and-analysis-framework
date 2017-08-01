package tests.utils.files.naming

import org.scalatest.FlatSpec

import tas.types.{
  Time,
  Interval
}

import tas.utils.files.naming._

class SourceFileNameTests extends FlatSpec {
  behavior of "SourceFileName"

  def testNaming(actual:String,
                 expected:String) = {
    it should ("format " + expected) in {
      assert(actual === expected)
    }
  }

  testNaming(SourceFileName("eurusd",
                            new Periods(Interval.minutes(1)),
                            Time.fromCalendar(2011, 10, 10),
                            Time.fromCalendar(2011, 11, 2),
                            new TicksFromPeriods("finam", "2020")),
             "finam-eurusd-periods-1m-2011-10-10---2011-11-02-from-periods-2020.txt")
}
