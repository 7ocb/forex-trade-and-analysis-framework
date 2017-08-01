package testing.soruces.timeshift

import org.scalatest.FlatSpec
import tas.sources.ticks.TimeShiftTickSource
import org.scalamock.scalatest.MockFactory

import tas.sources.ticks.TickSource
import tas.events.Event
import tas.events.SimpleEvent

import tas.types.{
  Interval,
  Price
}

import tas.testing.AsyncCallOrderTrace
import tas.timers.Timer

class TimeShiftTicksSourceTest extends FlatSpec with MockFactory with AsyncCallOrderTrace {
  behavior of "Time shifting ticks source"

  abstract class Test (timer:Timer) {

    def interval:Interval
    def newEvent:SimpleEvent[Price]
  
    val event = newEvent
    val slaveTicker = mock[TickSource]

    (slaveTicker.tickEvent _).expects().returning(event)
    
    assert(slaveTicker.tickEvent === event)

    (slaveTicker.tickEvent _).expects().returning(event)
    
    val shiftTicker = new TimeShiftTickSource(timer,
                                              interval,
                                              slaveTicker)

    val testTick = new Price("2.2", "2.3")
    
  } 

  it should "dont delay ticks if shift interval zero" in asyncTest (
    timer => {
      new Test(timer) {
        def interval = Interval.milliseconds(0)
        def newEvent = Event.newSync[Price]
        
        shiftTicker.tickEvent += (tick => {
                                    calledInOrder(1)
                                    tick == testTick
                                  } )

        calledInOrder(0)
        event << testTick
        calledInOrder(2)
      }})

  it should "delay ticks to shift interval" in asyncTest (
    timer => {
      new Test(timer) {

        def interval = Interval.milliseconds(2)
        def newEvent = Event.newSync[Price]
        

          val startTime = timer.currentTime
          
          shiftTicker.tickEvent += (tick => {
                                      calledInOrder(3)
                                      tick == testTick
                                      assert((startTime + interval) === timer.currentTime)

                                    } )

          calledInOrder(0)
          event << testTick
          calledInOrder(1)

          timer.after(Interval.milliseconds(1)) {
            calledInOrder(2)
          }
          
          timer.after(Interval.milliseconds(3)) {
            calledInOrder(4)
          }
        
      }})

  it should "delay ticks to shift interval with async event" in asyncTest (
    timer => {
      new Test(timer) {

        def interval = Interval.milliseconds(2)
        def newEvent = Event.newAsync[Price](timer)
        
        shiftTicker.tickEvent += (tick => {
                                    calledInOrder(3)
                                    tick == testTick
                                  } )

        calledInOrder(0)
        event << testTick
        calledInOrder(1)

        timer.after(Interval.milliseconds(1)) {
          calledInOrder(2)
        }
        
        timer.after(Interval.milliseconds(3)) {
          calledInOrder(4)
        }
        
      }})
  
} 
