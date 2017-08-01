package tests.timers

import tas.concurrency.RunLoop

import java.util.Calendar
import java.util.TimeZone

import java.text.SimpleDateFormat

import tas.types.Interval

import tas.timers.Timer
import tas.timers.RealTimeTimer  

import org.scalatest.FlatSpec
import org.scalamock.scalatest.MockFactory

import testing.utils.RunLoopSupport

class RealTimeTimerTest extends FlatSpec with RunLoopSupport {

  val timeFormat = new SimpleDateFormat("HH:mm")
  
  behavior of "RealTimeTimer"

  it should "post Now events to runLoop directly, avoiding timer" in runLoopTest {

    val action:()=>Unit = complete _
    
    val rtTimer = new RealTimeTimer(runLoop)

    rtTimer.callAt(Timer.Now, action)
  }

  it should "post time events to delayed run queue" in {
    val start = System.currentTimeMillis

    runLoopTest {

      def action = {
        info("action")
        complete 
      } 
      
      val rtTimer = new RealTimeTimer(runLoop)

      rtTimer.callAt(rtTimer.currentTime + Interval.milliseconds(100), action _)
    }
    
    val total = System.currentTimeMillis - start
    info("completed in: " + total + "ms")
    assert(total >= 100, "completed too early")
  }

  it should "report current time as UTC time" in  {
    val rtTimer = new RealTimeTimer(null)
  
    val cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"))
    info("current local time: " + timeFormat.format(cal.getTime()))

    val currentTime = rtTimer.currentTime
    
    assert(cal.get(Calendar.HOUR_OF_DAY) === currentTime.hours)
    assert(cal.get(Calendar.MINUTE) === currentTime.minutes)
    assert(cal.get(Calendar.YEAR) === currentTime.year)
    assert(cal.get(Calendar.MONTH) === (currentTime.month - 1))
    assert(cal.get(Calendar.DAY_OF_MONTH) === currentTime.day)
    info("UTC time: " + "%02d:%02d".format(currentTime.hours, currentTime.minutes))
  } 
} 
