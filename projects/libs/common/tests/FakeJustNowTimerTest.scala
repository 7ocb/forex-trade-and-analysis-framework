package testing.timers

import org.scalatest.FlatSpec

import tas.types.Time
import tas.types.Interval
import tas.timers.Timer
import tas.timers.JustNowFakeTimer

import scala.collection.mutable.ListBuffer

class FakeJustNowTimerTest extends FlatSpec {
  behavior of "fake just now timer"

  it should "call events in right order" in {
    val timer = new JustNowFakeTimer

    val timeCalls = List((10, 100),
                         (15, 300),
                         (11, 200),
                         (8, 80),
                         (3, 50),
                         (9, 80),
                         (4, 50),
                         (12, 200),
                         (13, 200),
                         (14, 200),
                         (5, 50),
                         (6, 60),
                         (7, 70),
                         (1, 20),
                         (2, 20))
    
    val output = new ListBuffer[Double]
    

    timeCalls.foreach(time => {
      timer.at(Time.milliseconds(time._2)) {
        output += time._1
      } 
    } )
    
    timer.loop


    val expected = output.sortWith(_ < _)

    assert(output === expected)
  }

  it should "correctly process second call to loop" in {
    val timer = new JustNowFakeTimer

    val output = new ListBuffer[Double]
    def submit(list:List[(Int, Int)]) = {
      list.foreach(time => {
                        timer.at(Time.milliseconds(time._2)) {
                          output += time._1
                        }
                      } )
    }

    val firstCalls = List((1, 80),
                          (2, 100),
                          (4, 200),
                          (3, 150))

    submit(firstCalls)
    


    timer.loop

    val secondCalls = List((5, 200),
                           (7, 300),
                           (6, 250))

    submit(secondCalls)

    timer.loop

    val expected = output.sortWith(_ < _)

    assert(output === expected)
  }
}      
