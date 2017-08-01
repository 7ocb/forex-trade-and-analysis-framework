package tests.readers

import org.scalatest.FlatSpec

import tas.types.PeriodBid
import tas.types.Time

import tas.input.format.Reader
import tas.readers.PeriodsSequence


class PeriodReaderTest extends FlatSpec {

  behavior of "PeriodReader"

  it should "correctly detect header and parse period" in {
    val data = ("<TICKER>	<PER>	<DATE>	<TIME>	<OPEN>	<HIGH>	<LOW>	<CLOSE>\n"
                  + "EURUSD	1	20110505	000000	1.48300	1.48310	1.48250	1.48280\n")

    val expected = new PeriodBid("1.483", "1.4828", "1.48250", "1.48310", Time.fromCalendar(2011, 5, 5))

    val parsed = PeriodsSequence.fromString(data)

    assert(parsed.all() === List(expected))
  }

  it should "throw format error if no data" in {
    intercept[Reader.FormatError] {
      val periods = PeriodsSequence.fromString("")
    }
  }

  it should "throw exception if haveNext == false and next is called" in {
    val periods = PeriodsSequence.fromString("<TICKER>	<PER>	<DATE>	<TIME>	<OPEN>	<HIGH>	<LOW>	<CLOSE>\n")
    assert(periods.haveNext === false)
    intercept[RuntimeException] {
      periods.next
    }
  }

  it should "next should return null in case if trash instead of data" in {
     val periods = PeriodsSequence.fromString("<TICKER>	<PER>	<DATE>	<TIME>	<OPEN>	<HIGH>	<LOW>	<CLOSE>\n"
                                               + "fadf alskjfl akdfkj asldfjlkjl\n")
    assert(periods.next === null)
  }
} 
