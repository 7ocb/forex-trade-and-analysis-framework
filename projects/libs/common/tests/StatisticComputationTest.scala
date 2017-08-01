package tests.sources

import org.scalatest.FlatSpec
import tas.timers.JustNowFakeTimer

import tas.sources.StatisticComputation
import tas.timers.JustNowFakeTimer

class StatisticComputationTest extends FlatSpec {

  "StatisticsComputation" should "correctly collect and compose data" in {
    val timer = new JustNowFakeTimer()

    var called = false

    val computation
    = new StatisticComputation[Int, Int](timer,
                                         3,
                                         list => {
                                           list(0) + list(1) + list(2)
                                         } )

    var expected = List(6, 9, 12)
    

    computation.updatedEvent += (data => {
                                   called = true

                                   assert(expected.head === data)

                                   expected = expected.tail
                                   
                                 } )

    computation.onInput(1)
    computation.onInput(2)
    computation.onInput(3)
    computation.onInput(4)
    computation.onInput(5)

    timer.loop
    
    assert(called, "Event not called!")
    assert(expected.size === 0)
  }
} 
