package tests.types

import org.scalatest.FlatSpec
import java.util.Date
import tas.types.Time
import tas.types.Time.NoDstGMTShift

import tas.types.{
  DayOfWeek,
  Monday,
  Tuesday,
  Wednesday,
  Thursday,
  Friday,
  Saturday,
  Sunday }


import tas.types.Interval

import tas.utils.parsers.TimeParser
import tas.utils.parsers.IntervalParser
import tas.utils.parsers.FormatError


import java.util.Calendar
import java.util.TimeZone

class TimeTests extends FlatSpec {
  behavior of "Time"


  it should "recreate it's from calendar" in {
    val time = Time.milliseconds(new Date().getTime())
    val recreatedTime = Time.fromCalendar(time.year,
                                          time.month,
                                          time.day,
                                          time.hours,
                                          time.minutes,
                                          time.seconds,
                                          time.milliseconds)

    assert(time === recreatedTime)
  }

  it should "correctly restore from ms from utc calendar" in {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"))

    val time = Time.milliseconds(cal.getTime().getTime())

    assert(cal.get(Calendar.HOUR_OF_DAY) === time.hours)
    assert(cal.get(Calendar.MINUTE) === time.minutes)
    assert(cal.get(Calendar.YEAR) === time.year)
    assert(cal.get(Calendar.MONTH) === (time.month - 1))
    assert(cal.get(Calendar.DAY_OF_MONTH) === time.day)
  
  }

  behavior of "TimeParser"

  def testParse(str:String, expectedTime:Time) = {
    it should ("parse " + expectedTime + " from \"" + str + "\"") in {
      val time = TimeParser(str)
      assert(time === expectedTime)
    } 
  }

  def testError(str:String, reason:String) = {
    it should ("throw error parsing \"" + str + "\" because " + reason) in {
      intercept[FormatError] {
        TimeParser(str)
      } 
    } 
  } 

  testParse("2001.02.13", Time.fromCalendar(2001, 2, 13))
  testParse("2001.02.13/20:01", Time.fromCalendar(2001, 2, 13, 20, 1, 0))
  testParse("2001.02.13/20:01:02", Time.fromCalendar(2001, 2, 13, 20, 1, 2))
  testParse("2001.02.13/20:01:02.33", Time.fromCalendar(2001, 2, 13, 20, 1, 2, 33))

  testError("2001.02,13", "comma instead of point")
  testError("2001.02/13", "slash instead of point")
  testError("2001.02.13/13", "minutes not specified")
  testError("2001.02.13/13:ab", "minutes not a number")
  testError("ab.02.13/13:01", "year not a number")
  testError("2001.ab.13/13:01", "month not a number")
  testError("2001.03.aa/13:01", "day not a number")
  testError("2001.03.02/aa:01", "hour not a number")
  testError("2001.50.02/12:01", "wrong month")
  testError("2001.0.02/12:01", "wrong month")
  testError("2001.10.02/60:01", "wrong hour")
  testError("2001.10.02/20:01.02", "second split with dot instead of :")
  testError("2001.10.02/20:60:02", "wrong minute")
  testError("2001.10.02/20:45:60", "wrong second")
  testError("2001.10.02/20:45:30.3030", "wrong millisecond")
  testError("2001.10.02/20:45:30:3030", "wrong millisecond separator")

  it should "correclty convert to GMT from GMT+1" in {
    val shifted = Time.fromCalendar(2000, 10, 10, 20, 15, 0)
    val gmt = shifted.toUtcFrom(new Time.NoDstGMTShift(1))
    val expected = Time.fromCalendar(2000, 10, 10, 19, 15, 0)
    assert(gmt === expected)
  }

  it should "correclty convert to GMT+1 from GMT" in {
    val gmt = Time.fromCalendar(2000, 10, 10, 19, 15, 0)
    val result = gmt.fromUtcTo(new Time.NoDstGMTShift(1))
    val expectedHours = 20
    assert(result.hours === expectedHours)
  }

  def shiftTest(description:String, original:Time, shift:Time=>Time, expected:Time) = {
    it should ("shift " + description) in {
      val shifted = shift(original)
       assert(shifted != original)
      assert(shifted === expected)
    } 
  } 

  shiftTest("years up",
            Time.fromCalendar(2010, 10, 10, 10, 10, 10),
            _.shifted(shiftYears = 1),
            Time.fromCalendar(2011, 10, 10, 10, 10, 10))

   shiftTest("years down",
            Time.fromCalendar(2010, 10, 10, 10, 10, 10),
            _.shifted(shiftYears = -1),
            Time.fromCalendar(2009, 10, 10, 10, 10, 10))

  shiftTest("months up",
            Time.fromCalendar(2010, 10, 10, 10, 10, 10),
            _.shifted(shiftMonths = 1),
            Time.fromCalendar(2010, 11, 10, 10, 10, 10))

  shiftTest("months down",
            Time.fromCalendar(2010, 10, 10, 10, 10, 10),
            _.shifted(shiftMonths = -1),
            Time.fromCalendar(2010, 9, 10, 10, 10, 10))

  shiftTest("days up",
            Time.fromCalendar(2010, 10, 10, 10, 10, 10),
            _.shifted(shiftDays = 1),
            Time.fromCalendar(2010, 10, 11, 10, 10, 10))

  shiftTest("days down",
            Time.fromCalendar(2010, 10, 10, 10, 10, 10),
            _.shifted(shiftDays = -1),
            Time.fromCalendar(2010, 10, 9, 10, 10, 10))

  shiftTest("hours up",
            Time.fromCalendar(2010, 10, 10, 10, 10, 10),
            _.shifted(shiftHours = 1),
            Time.fromCalendar(2010, 10, 10, 11, 10, 10))

  shiftTest("hours down",
            Time.fromCalendar(2010, 10, 10, 10, 10, 10),
            _.shifted(shiftHours = -1),
            Time.fromCalendar(2010, 10, 10, 9, 10, 10))

  shiftTest("minutes up",
            Time.fromCalendar(2010, 10, 10, 10, 10, 10),
            _.shifted(shiftMinutes = 1),
            Time.fromCalendar(2010, 10, 10, 10, 11, 10))

  shiftTest("minutes down",
            Time.fromCalendar(2010, 10, 10, 10, 10, 10),
            _.shifted(shiftMinutes = -1),
            Time.fromCalendar(2010, 10, 10, 10, 9, 10))

  shiftTest("seconds up",
            Time.fromCalendar(2010, 10, 10, 10, 10, 10),
            _.shifted(shiftSeconds = 1),
            Time.fromCalendar(2010, 10, 10, 10, 10, 11))

  shiftTest("seconds down",
            Time.fromCalendar(2010, 10, 10, 10, 10, 10),
            _.shifted(shiftSeconds = -1),
            Time.fromCalendar(2010, 10, 10, 10, 10, 9))

  shiftTest("milliseconds up",
            Time.fromCalendar(2010, 10, 10, 10, 10, 10, 10),
            _.shifted(shiftMilliseconds = 1),
            Time.fromCalendar(2010, 10, 10, 10, 10, 10, 11))

  shiftTest("milliseconds down",
            Time.fromCalendar(2010, 10, 10, 10, 10, 10, 10),
            _.shifted(shiftMilliseconds = -1),
            Time.fromCalendar(2010, 10, 10, 10, 10, 10, 9))


  def testIntervalParser(str:String, expected:Interval) = {
    it should ("parse " + expected + " from " + str) in {
      val interval = IntervalParser(str)
      assert(interval === expected)
    } 
  }

  def testIntervalParserError(str:String) = {
    it should ("throw error parsing " + str) in {
      intercept[FormatError] {
        IntervalParser(str)
      } 
    } 
  }


  testIntervalParser("3s", Interval.seconds(3))
  testIntervalParser("4h", Interval.hours(4))
  testIntervalParser("5m", Interval.minutes(5))
  testIntervalParser("8ms", Interval.milliseconds(8))
  testIntervalParser("9d", Interval.days(9))
  testIntervalParser("9d6h2m11s13ms", Interval.time(9, 6, 2, 11, 13))

  testIntervalParserError("")
  testIntervalParserError("asdfasd")
  testIntervalParserError("9adsf")

  behavior of "interval"

  it should "detect next start of interval in day" in {
    assert(Interval.time(0, 1, 30, 0).findNextStartInDay(Time.fromCalendar(2010, 1, 1, 20, 20, 11))
           === Time.fromCalendar(2010, 1, 1, 21, 0, 0))

    assert(Interval.time(0, 1, 0, 0).findNextStartInDay(Time.fromCalendar(2010, 1, 1, 20, 20, 11))
           === Time.fromCalendar(2010, 1, 1, 21, 0, 0))

    assert(Interval.time(0, 0, 15, 0).findNextStartInDay(Time.fromCalendar(2010, 1, 1, 20, 20, 11))
           === Time.fromCalendar(2010, 1, 1, 20, 30, 0))
  }

  it should "throw Interval.Error if day % interval != 0" in {
    intercept[Interval.Error] {
      Interval.time(0, 0, 17, 0).findNextStartInDay(Time.fromCalendar(2010, 1, 1, 20, 20, 11))
    } 
  }

  it should "throw Interval.Error if interval > day" in {
    intercept[Interval.Error] {
      Interval.time(1, 0, 17, 0).findNextStartInDay(Time.fromCalendar(2010, 1, 1, 20, 20, 11))
    } 
  }

  it should "detect next start of interval even if it start of next day" in {
    assert(Interval.time(0, 4, 0, 0).findNextStartInDay(Time.fromCalendar(2010, 1, 1, 20, 20, 11))
           === Time.fromCalendar(2010, 1, 2, 0, 0, 0))
  } 


  def testDayOfWeek(time:Time, expectedDayOfWeek:DayOfWeek) = {
    it should ("report " + expectedDayOfWeek + " at " + time) in {
      assert(time.dayOfWeek === expectedDayOfWeek)
    }
  }

  testDayOfWeek(Time.fromCalendar(2013, 8, 12), Monday)
  testDayOfWeek(Time.fromCalendar(2013, 8, 13), Tuesday)
  testDayOfWeek(Time.fromCalendar(2013, 8, 14), Wednesday)
  testDayOfWeek(Time.fromCalendar(2013, 8, 15), Thursday)
  testDayOfWeek(Time.fromCalendar(2013, 8, 16), Friday)
  testDayOfWeek(Time.fromCalendar(2013, 8, 17), Saturday)
  testDayOfWeek(Time.fromCalendar(2013, 8, 18), Sunday)

  it should "correctly find start of week" in {
    val startOfWeek = Time.fromCalendar(2013, 8, 12)

    assert(startOfWeek.startOfWeek === startOfWeek)

    assert(startOfWeek === Time.fromCalendar(2013, 8, 12, 0, 1, 2).startOfWeek)
    assert(startOfWeek === Time.fromCalendar(2013, 8, 17, 20, 0, 0).startOfWeek)
  }

  def testDayOfWeekShift(dayOfWeekFrom:DayOfWeek, dayOfWeekTo:DayOfWeek, expected:Int) = {
    it should ("show distance " + expected + " from " + dayOfWeekFrom + " to " + dayOfWeekTo) in {
      assert((dayOfWeekFrom.daysToNext(dayOfWeekTo)) === expected)
    }
  }

  testDayOfWeekShift(Monday, Monday,    0)
  testDayOfWeekShift(Monday, Tuesday,   1)
  testDayOfWeekShift(Monday, Wednesday, 2)
  testDayOfWeekShift(Tuesday, Monday,   6)
  testDayOfWeekShift(Saturday, Monday,  2)


  it should "correctly calculate next time" in {
    assert(Time.fromCalendar(2013, 8, 12, 10, 0, 0).nextTime(Monday,
                                                             Interval.time(0, 10, 0, 0))
             === Time.fromCalendar(2013, 8, 19, 10, 0, 0))

    assert(Time.fromCalendar(2013, 8, 12, 9, 0, 0).nextTime(Monday,
                                                            Interval.time(0, 10, 0, 0))
             === Time.fromCalendar(2013, 8, 12, 10, 0, 0))

    assert(Time.fromCalendar(2013, 8, 12, 11, 0, 0).nextTime(Monday,
                                                             Interval.time(0, 10, 0, 0))
             === Time.fromCalendar(2013, 8, 19, 10, 0, 0))
  }


  it should "correctly find start of day in shifted time" in {
    val gmt = Time.fromCalendar(2000, 10, 10, 19, 15, 0)

    val shiftZone = new Time.NoDstGMTShift(1)

    val shifted = gmt.fromUtcTo(shiftZone)

    val startOfDayShifted = shifted.startOfDay

    val startOfShiftedDay = startOfDayShifted.toUtcFrom(shiftZone)

    val expected = Time.fromCalendar(2000, 10, 9, 23, 0, 0)

    assert(startOfShiftedDay === expected)
  }

  behavior of "next period start time"


  def checkPeriodTimeLocation(baseTime:Time,
                              period:Interval,
                              periodStartShift:Interval,
                              startTime:Time) = {

    it should ("locate next start time: " + startTime + " from " + baseTime + "\n  with period " + period + " and shift " + periodStartShift) in {
      assert(baseTime.nextPeriodStartTime(period,
                                          periodStartShift)
               === startTime)
    }
  }


  checkPeriodTimeLocation(Time.fromCalendar(2002, 1, 1, 15, 0, 0),
                          Interval.days(1),
                          Interval.ZERO,
                          Time.fromCalendar(2002, 1, 2, 0, 0, 0))

  checkPeriodTimeLocation(Time.fromCalendar(2002, 1, 1, 15, 0, 0),
                          Interval.days(1),
                          Interval.minutes(10),
                          Time.fromCalendar(2002, 1, 2, 0, 10, 0))

  checkPeriodTimeLocation(Time.fromCalendar(2002, 1, 1, 0, 55, 0),
                          Interval.hours(1),
                          Interval.minutes(10),
                          Time.fromCalendar(2002, 1, 1, 1, 10, 0))

  checkPeriodTimeLocation(Time.fromCalendar(2002, 1, 1, 0, 55, 0),
                          Interval.hours(1),
                          Interval.minutes(70),
                          Time.fromCalendar(2002, 1, 1, 1, 10, 0))

}
