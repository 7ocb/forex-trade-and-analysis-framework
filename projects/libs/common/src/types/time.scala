package tas.types

import tas.utils.format.StringComplementor

import java.util.Date
import java.util.Calendar
import java.util.GregorianCalendar

import java.util.TimeZone

import scala.annotation.tailrec

object Interval {

  val ZERO = Interval.milliseconds(0)

  class Error(val message:String) extends Exception(message)
  
  private val MsInDay = 24 * 60 * 60 * 1000
  private val MsInHour = 60 * 60 * 1000
  private val MsInMin = 60 * 1000
  private val MsInSec = 1000

  def milliseconds(msec:Long):Interval = {
    new Interval(msec)
  }

  def time(days:Long , hours:Long , minutes:Long , seconds:Long , msecs:Long ):Interval =
    milliseconds(days * MsInDay
                 + hours * MsInHour
                 + minutes * MsInMin
                 + seconds * MsInSec
                 + msecs)

  def time(days:Long , hours:Long , minutes:Long , seconds:Long ):Interval = time(days, hours, minutes, seconds, 0)

  def minutes(minutes:Long):Interval = time(0, 0, minutes, 0, 0)
  def seconds(seconds:Long):Interval = time(0, 0, 0, seconds, 0)
  def hours(hours:Long):Interval = time(0, hours, 0, 0, 0)
  def days(days:Long):Interval = time(days, 0, 0, 0, 0)

}

class Interval private[Interval](val milliseconds:Long) extends Ordered[Interval] with Serializable {

  import Interval.MsInDay
  import Interval.MsInHour
  import Interval.MsInMin
  import Interval.MsInSec
  
  def /(divider:Int):Interval = Interval.milliseconds(milliseconds / divider)
  def /(divider:Interval):Int = (milliseconds / divider.milliseconds).asInstanceOf[Int]
  def *(multiplier:Int):Interval = Interval.milliseconds(milliseconds * multiplier)
  def +(other:Interval):Interval = Interval.milliseconds(milliseconds + other.milliseconds)

  override def compare(that:Interval) = milliseconds.compare(that.milliseconds)
  override def equals(that:Any):Boolean = that.isInstanceOf[Interval] && compare(that.asInstanceOf[Interval]) == 0

  lazy val partDays = milliseconds / MsInDay
  lazy val partHours = (milliseconds % MsInDay) / MsInHour
  lazy val partMinutes = ((milliseconds % MsInDay) % MsInHour) / MsInMin
  lazy val partSeconds = (((milliseconds % MsInDay) % MsInHour) % MsInMin) / MsInSec
  lazy val partMilliseconds = milliseconds % MsInSec

  override def toString:String = {

    def complemented(v:Long, len:Int = 2) = new StringComplementor(len, "0")(v.toString)


    val daysString = if (partDays > 1) { "" + partDays + " days + " }
                     else if (partDays > 0) { "1 day + " }
                     else ""

    daysString + partHours + ":" + complemented(partMinutes) + ":" + complemented(partSeconds) + "." + complemented(partMilliseconds, 3)
  }

  def toStringShortForm = {
    def printIfPositive(value:Long, postfix:String) = {
      if (value > 0) {
        "" + value + postfix
      } else {
        ""
      }
    }

    (printIfPositive(partDays, "d")
       + printIfPositive(partHours, "h")
       + printIfPositive(partMinutes, "m")
       + printIfPositive(partSeconds, "s")
       + printIfPositive(partMilliseconds, "ms"))
  }


  def isZero = milliseconds == 0

  def isCanFillDay = {
    (this <= Interval.days(1)
     && (Interval.days(1).milliseconds % this.milliseconds == 0))
  } 
    
  
  def findNextStartInDay(time:Time):Time = {
    if ( ! isCanFillDay ) throw new Interval.Error("Interval can't fill in day!")
  
    val startOfDay = time.shifted(shiftHours = -time.hours,
                                  shiftMinutes = -time.minutes,
                                  shiftSeconds = -time.seconds,
                                  shiftMilliseconds = -time.milliseconds)

    val end = startOfDay.shifted(shiftDays = 1)

    var check = startOfDay
    
    while (check <= end) {
      if (check > time) {
        return check
      }
      check += this
    }

    throw new RuntimeException("Should never happen")
  } 
} 

object DayOfWeek {
  private [types] def fromCalendarDayOfWeek(dayOfWeek:Int) = {
    dayOfWeek match {
      case Calendar.MONDAY => Monday
      case Calendar.TUESDAY => Tuesday
      case Calendar.WEDNESDAY => Wednesday
      case Calendar.THURSDAY => Thursday
      case Calendar.FRIDAY => Friday
      case Calendar.SATURDAY => Saturday
      case Calendar.SUNDAY => Sunday
    }
  }
}

sealed class DayOfWeek(protected val index:Int) {
  def daysToNext(dayOfWeek:DayOfWeek) = {
    if (dayOfWeek.index >= index) dayOfWeek.index - index
    else 7 - (index - dayOfWeek.index)
  }
}

case object Monday extends DayOfWeek(0)
case object Tuesday extends DayOfWeek(1)
case object Wednesday extends DayOfWeek(2)
case object Thursday extends DayOfWeek(3)
case object Friday extends DayOfWeek(4)
case object Saturday extends DayOfWeek(5)
case object Sunday extends DayOfWeek(6)




object Time {

  sealed class Timezone(timeZoneName:String) {
    private [Time] def newGregorianCalendar() = new GregorianCalendar(javaTimeZone)
    private [Time] lazy val javaTimeZone = TimeZone.getTimeZone(timeZoneName)
  }
  case object Moscow extends Timezone("Europe/Moscow")
  case object GMT extends Timezone("GMT")

  case class NoDstGMTShift(val hours:Int) extends Timezone("GMT" + {
                                                             if (hours > 0) ("+" + hours)
                                                             else if (hours < 0) ("-" + (-hours))
                                                             else ""
                                                           } )

  val DefaultTimeZone = GMT

  private def createCalendar = createCalendarWithTimeZone(DefaultTimeZone)
  private def createCalendarWithTimeZone(timezone:Timezone) = timezone.newGregorianCalendar()

  def milliseconds(msec:Long):Time = new Time(new Date(msec), null)

  def fromCalendar(year:Int, month:Int, day:Int, hour:Int, minute:Int, second:Int, millisecond:Int):Time = {
    val calendar = createCalendar
    
    calendar.set(year, month - 1, day, hour, minute, second)
    calendar.set(Calendar.MILLISECOND, millisecond)

    new Time(null, calendar)
  }

  def fromCalendar(year:Int, month:Int, day:Int, hour:Int, minute:Int, second:Int):Time = fromCalendar(year, month, day, hour, minute, second, 0)
  def fromCalendar(year:Int, month:Int, day:Int):Time = fromCalendar(year, month, day, 0, 0, 0, 0)

  private val TimeFormatterPattern = "yyyy.MM.dd HH:mm:ss.SSS"

  
  def formatTime(calendar:Calendar) = {
    val formatter = new java.text.SimpleDateFormat(TimeFormatterPattern)
    formatter.setTimeZone(calendar.getTimeZone())
    formatter.format(calendar.getTime())
  }

  def now:Time = {
    val millisecondsUTC = Calendar.getInstance().getTime().getTime()
    Time.milliseconds(millisecondsUTC)
  }
} 

class Time private[Time](initDate:Date, initCalendar:Calendar) extends Ordered[Time] with Serializable {
  import Time.Timezone

  if (initDate == null && initCalendar == null) throw new Error("date and calendar can not be null")
  
  private lazy val date = {
    if (initDate != null) initDate
    else initCalendar.getTime()
  }

  private lazy val calendar = {
    if (initCalendar != null) initCalendar
    else {
      val calendar = Time.createCalendar
      calendar.setTime(initDate)
      calendar
    } 
  }
  
  override def compare(that:Time) = if (that eq this) 0
                                    else date.compareTo(that.date)

  override def equals(that:Any):Boolean = that.isInstanceOf[Time] && compare(that.asInstanceOf[Time]) == 0

  private def withAddedMilliseconds(ms:Long) = {
    val newCalendar = calendar.clone.asInstanceOf[Calendar]
    newCalendar.setTime(new Date(calendar.getTime().getTime() + ms))
    new Time(null, newCalendar)
  }

  def +(interval:Interval):Time = if (interval == Interval.ZERO) this
                                  else withAddedMilliseconds(interval.milliseconds)

  def -(interval:Interval):Time = if (interval == Interval.ZERO) this
                                  else withAddedMilliseconds( -interval.milliseconds )

  def -(time:Time):Interval = Interval.milliseconds(scala.math.abs(date.getTime() - time.date.getTime()))

  private def get(what:Int) = calendar get what
  private def add(what:Int, howMany:Int) = calendar.add(what, howMany)

  private def convertTimeZone(from:Timezone,
                              to:Timezone) = {
    val otherCalendar = Time.createCalendarWithTimeZone(from)
    otherCalendar.set(year, month - 1, day, hours, minutes, seconds)
    otherCalendar.set(Calendar.MILLISECOND, milliseconds)

    val newTimeCalendar = Time.createCalendarWithTimeZone(to)
    newTimeCalendar.setTime(otherCalendar.getTime())

    new Time(null, newTimeCalendar)
  }

  def toUtcFrom(timezone:Timezone):Time = {
    if (timezone == Time.DefaultTimeZone) this
    else convertTimeZone(timezone, Time.DefaultTimeZone)
  }

  def fromUtcTo(timezone:Timezone):Time = {
    convertTimeZone(Time.DefaultTimeZone, timezone)
  }

  def nextTime(targetDayOfWeek:DayOfWeek, targetShiftFromStartOfDay:Interval) = {
    ( if (targetDayOfWeek == dayOfWeek) {
       if (targetShiftFromStartOfDay <= fromStartOfDay) startOfDay.shifted(shiftDays = 7)
       else startOfDay
     } else {
       startOfDay.shifted(shiftDays = dayOfWeek.daysToNext(targetDayOfWeek))
     } ) + targetShiftFromStartOfDay
  }

  lazy val startOfWeek:Time = startOfDay.shifted(shiftDays = -(Monday.daysToNext(startOfDay.dayOfWeek)))

  lazy val startOfDay:Time = (this - fromStartOfDay)

  lazy val fromStartOfDay:Interval = Interval.time(0, hours, minutes, seconds, milliseconds)

  lazy val startOfNextDay = startOfDay.shifted(shiftDays = 1)

  lazy val year = get(Calendar.YEAR)
  lazy val month = get(Calendar.MONTH) + 1
  lazy val day = get(Calendar.DAY_OF_MONTH)

  lazy val hours = get(Calendar.HOUR_OF_DAY)
  lazy val minutes = get(Calendar.MINUTE)
  lazy val seconds = get(Calendar.SECOND)
  
  lazy val milliseconds = get(Calendar.MILLISECOND)

  lazy val underlyingMilliseconds = date.getTime()

  lazy val dayOfWeek:DayOfWeek = DayOfWeek.fromCalendarDayOfWeek(get(Calendar.DAY_OF_WEEK))

  override def toString():String = Time.formatTime(calendar)

  override def clone = new Time(null, calendar.clone.asInstanceOf[Calendar])

  def shifted(shiftYears:Int = 0,
              shiftMonths:Int = 0,
              shiftDays:Int = 0,
              shiftHours:Int = 0,
              shiftMinutes:Int = 0,
              shiftSeconds:Int = 0,
              shiftMilliseconds:Int = 0) = {
    val shiftedTime = clone

    shiftedTime.add(Calendar.YEAR, shiftYears)
    shiftedTime.add(Calendar.MONTH, shiftMonths)
    shiftedTime.add(Calendar.DAY_OF_MONTH, shiftDays)
    shiftedTime.add(Calendar.HOUR_OF_DAY, shiftHours)
    shiftedTime.add(Calendar.MINUTE, shiftMinutes)
    shiftedTime.add(Calendar.SECOND, shiftSeconds)
    shiftedTime.add(Calendar.MILLISECOND, shiftMilliseconds)

    shiftedTime
  }

  def nextPeriodStartTime(period:Interval,
                          shift:Interval = Interval.ZERO) = {

    val startTime = period.findNextStartInDay(this) + shift

    @tailrec def closerToThis(candidate:Time):Time = {
      val shifted = candidate - period
      if (shifted > this) closerToThis(shifted)
      else candidate
    }

    closerToThis(startTime)
  }

}

