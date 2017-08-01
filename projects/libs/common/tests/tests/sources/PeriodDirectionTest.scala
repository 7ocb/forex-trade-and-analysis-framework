package tests.sources

import tas.events.Event
import tas.types.{
  Period,
  Price
}

import tas.sources.periods.PeriodSource
import tas.sources.PeriodDirection

import org.scalatest.FlatSpec
import org.scalamock.scalatest.MockFactory

import tas.testing.TimerSupport
import tas.timers.Timer

class PeriodDirectionTest extends FlatSpec with MockFactory with TimerSupport {
  behavior of "PeriodDirection"

  import PeriodDirection.Direction
  import PeriodDirection.Up
  import PeriodDirection.Down
  import PeriodDirection.NoDirection
  
  it should "be created without logger argument from event" in timerRun {
    val event = Event.newAsync[Period](timer)
    new PeriodDirection(timer, event)
  }

  it should "be created without logger argument from periods source" in timerRun {
    val event = Event.newAsync[Period](timer)

    val source = mock[PeriodSource]
    (source.periodCompleted _).expects().returning(event)
  
    new PeriodDirection(timer, source)
  }

  class Fixture(timer:Timer) {
    val event = Event.newAsync[Period](timer)
    val direction = new PeriodDirection(timer, event)
  } 

  def testPeriodDirection(period:Period, direction:Direction) = {
    it should ("detect direction " + direction + " from period " + period) in timerRun {
      val f = new Fixture(timer)

      val receiver = mock[(Direction)=>Unit]
      (receiver.apply _).expects(direction)
      f.direction.onValueSet += receiver
      f.event << period
    }    
  }

  testPeriodDirection(new Period(Price.fromBid(0, 0),
                                 Price.fromBid(1, 0),
                                 Price.fromBid(0, 0),
                                 Price.fromBid(0, 0), null),
                      Up)

  testPeriodDirection(new Period(Price.fromBid(1, 0),
                                 Price.fromBid(0, 0),
                                 Price.fromBid(0, 0),
                                 Price.fromBid(0, 0), null),
                      Down)

  testPeriodDirection(new Period(Price.fromBid(1, 0),
                                 Price.fromBid(1, 0),
                                 Price.fromBid(0, 0),
                                 Price.fromBid(0, 0), null),
                      NoDirection)
  
 
} 
