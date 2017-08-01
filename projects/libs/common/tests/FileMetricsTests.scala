package tests.readers

import org.scalatest.FlatSpec
import tas.readers.TicksFileMetrics
import tas.readers.PeriodsFileMetrics

import tas.types.{
  Time,
  Interval
}

class FileMetricsTests extends FlatSpec {

  "PeriodsFileMetrics" should "detect metrics from string" in {

    val data = ("<TICKER>	<PER>	<DATE>	<TIME>	<OPEN>	<HIGH>	<LOW>	<CLOSE>\n"
                  + "EURUSD	1	20110505	000000	1.48300	1.48310	1.48250	1.48280\n"
                  + "EURUSD	1	20110505	000100	1.48290	1.48300	1.48230	1.48290\n"
                  + "EURUSD	1	20110505	000200	1.48280	1.48300	1.48250	1.48290\n"
                  + "EURUSD	1	20110505	000300	1.48280	1.48300	1.48260	1.48300\n");

    val metrics = PeriodsFileMetrics.fromString(data)

    assert(metrics.firstStartTime === Time.fromCalendar(2011, 5, 5, 0, 0, 0))
    assert(metrics.lastStartTime === Time.fromCalendar(2011, 5, 5, 0, 3, 0))
    assert(metrics.interval === Interval.minutes(1))

  }

  "TicksFileMetrics" should "detect metrics from string" in {
    val data = ("2010-12-31-21-00-12 1.3371\n"
                  + "2010-12-31-21-00-24 1.3371\n"
                  + "2010-12-31-21-01-15 1.33662362470789706774\n"
                  + "2010-12-31-21-01-22 1.337\n"
                  + "2010-12-31-21-01-30 1.33669029011827372496\n"
                  + "2010-12-31-21-01-52 1.337\n"
                  + "2010-12-31-21-02-07 1.3368\n"
                  + "2010-12-31-21-02-15 1.33665537330303649406\n"
                  + "2010-12-31-21-02-37 1.3369\n"
                  + "2010-12-31-21-02-45 1.336575601572268123184\n"
                  + "2010-12-31-21-02-52 1.3365\n"
                  + "2010-12-31-21-03-08 1.3369\n"
                  + "2010-12-31-21-03-17 1.3366\n")

    val metrics = TicksFileMetrics.fromString(data)

    assert(metrics.firstTickTime === Time.fromCalendar(2010, 12, 31, 21, 0, 12))
    assert(metrics.lastTickTime  === Time.fromCalendar(2010, 12, 31, 21, 3, 17))

  }

}
