package tests.sources

import org.scalatest.FlatSpec

import tas.types.{
  Time,
  Interval,
  Fraction,
  Period
}

import tas.timers.Timer
import tas.timers.JustNowFakeTimer
import tas.events.Event

import tas.sources.ticks.decomposer.PeriodToOpenMinMaxClose
import tas.sources.ticks.PeriodsToTicksSource
import tas.readers.PeriodsSequence
import tas.readers.PeriodsFileMetrics
import tas.sources.periods.Ticks2Periods

class TicksToPeriodsTest extends FlatSpec {

  "Ticks2Periods" should "correctly compose periods from ticks" in {

    var data = "<TICKER>	<PER>	<DATE>	<TIME>	<OPEN>	<HIGH>	<LOW>	<CLOSE>\n"
    data += "EURUSD	1	20110505	000000	1.48300	1.48310	1.48250	1.48280\n"
    data += "EURUSD	1	20110505	000100	1.48290	1.48300	1.48230	1.48290\n"
    data += "EURUSD	1	20110505	000200	1.48280	1.48300	1.48250	1.48290\n"
    data += "EURUSD	1	20110505	000300	1.48280	1.48300	1.48260	1.48300\n";

    val spread = Fraction("1")

    val metrics = PeriodsFileMetrics.fromString(data)
    
    var expected = PeriodsSequence.fromString(data).all().map(periodBid => {
                                                                Period.fromBid(periodBid,
                                                                               spread)
                                                              } )
    val periodsSource = PeriodsSequence.fromString(data)

    val interval = Interval.minutes(1)

    var called = false
    
    JustNowFakeTimer (
      timer => {
        val ticksSource = new PeriodsToTicksSource(timer,
                                                   spread,
                                                   new PeriodToOpenMinMaxClose(interval),
                                                   periodsSource)

        val periodsComposer = new Ticks2Periods(timer,
                                                ticksSource.tickEvent,
                                                interval,
                                                metrics.firstStartTime,
                                                metrics.lastStartTime + metrics.interval)
        
        periodsComposer.periodCompleted += (
          period => {
            called = true

            assert(expected.size > 0, "unexpected period completed event")
            
            val expectedPeriod = expected.head
            expected = expected.tail

            import expectedPeriod._

            assert(period === expectedPeriod)
          })
      })

    assert(called, "Event not called!")
  } 
} 
