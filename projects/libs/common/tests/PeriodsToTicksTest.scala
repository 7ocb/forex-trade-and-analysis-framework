package tests.sources

import org.scalatest.FlatSpec

import tas.types.{
  Time,
  Interval,
  Fraction,
  Price
}

import tas.timers.Timer
import tas.timers.JustNowFakeTimer
import tas.events.Event

import tas.sources.ticks.decomposer.PeriodToOpenMinMaxClose

import tas.sources.ticks.PeriodsToTicksSource
import tas.readers.PeriodsSequence


class PeriodsToTicksTest extends FlatSpec {

  "PeriodsToTicksSource" should "correctly dispatch periods" in {
    
    var data = "<TICKER>	<PER>	<DATE>	<TIME>	<OPEN>	<HIGH>	<LOW>	<CLOSE>\n"
    data += "EURUSD	1	20110505	000000	1.48300	1.48310	1.48250	1.48280\n"
    data += "EURUSD	1	20110505	000100	1.48290	1.48300	1.48230	1.48290\n"
    data += "EURUSD	1	20110505	000200	1.48280	1.48300	1.48250	1.48290\n"
    data += "EURUSD	1	20110505	000300	1.48280	1.48300	1.48260	1.48300\n";

    var expected = List((Time.fromCalendar(2011,5,5, 0,0,12), new Price(Fraction(1.483), Fraction(1.4831))),
                        (Time.fromCalendar(2011,5,5, 0,0,24), new Price(Fraction(1.4825), Fraction(1.4826))),
                        (Time.fromCalendar(2011,5,5, 0,0,36), new Price(Fraction(1.4831), Fraction(1.4832))),
                        (Time.fromCalendar(2011,5,5, 0,0,48), new Price(Fraction(1.4828), Fraction(1.4829))),
                        (Time.fromCalendar(2011,5,5, 0,1,12), new Price(Fraction(1.4829), Fraction(1.4830))),
                        (Time.fromCalendar(2011,5,5, 0,1,24), new Price(Fraction(1.4823), Fraction(1.4824))),
                        (Time.fromCalendar(2011,5,5, 0,1,36), new Price(Fraction(1.483), Fraction(1.4831))),
                        (Time.fromCalendar(2011,5,5, 0,1,48), new Price(Fraction(1.4829), Fraction(1.4830))),
                        (Time.fromCalendar(2011,5,5, 0,2,12), new Price(Fraction(1.4828), Fraction(1.4829))),
                        (Time.fromCalendar(2011,5,5, 0,2,24), new Price(Fraction(1.4825), Fraction(1.4826))),
                        (Time.fromCalendar(2011,5,5, 0,2,36), new Price(Fraction(1.483), Fraction(1.4831))),
                        (Time.fromCalendar(2011,5,5, 0,2,48), new Price(Fraction(1.4829), Fraction(1.4830))),
                        (Time.fromCalendar(2011,5,5, 0,3,12), new Price(Fraction(1.4828), Fraction(1.4829))),
                        (Time.fromCalendar(2011,5,5, 0,3,24), new Price(Fraction(1.4826), Fraction(1.4827))),
                        (Time.fromCalendar(2011,5,5, 0,3,36), new Price(Fraction(1.483), Fraction(1.4831))),
                        (Time.fromCalendar(2011,5,5, 0,3,48), new Price(Fraction(1.483), Fraction(1.4831))))

    var called = false  

    JustNowFakeTimer (
      timer => {

        val source = new PeriodsToTicksSource(timer,
                                              Fraction("0.0001"),
                                              new PeriodToOpenMinMaxClose(Interval.minutes(1)),
                                              PeriodsSequence.fromString(data))

        source.tickEvent += (
          price => {

            called = true

            assert(expected.size > 0, "Unexpected event call")

            val priceExpected = expected.head._2

            assert(price === priceExpected)

            val timeExpected = expected.head._1

            assert(timeExpected === timer.currentTime)

            expected = expected.tail
          })

      })


    assert(called, "Event not called!")
  } 
} 
