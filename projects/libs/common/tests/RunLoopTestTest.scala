package testing.tools

import org.scalatest.FlatSpec
import org.scalamock.scalatest.MockFactory

import testing.utils.RunLoopSupport
import tas.types.Interval

class RunLoopTestTest extends FlatSpec with RunLoopSupport with MockFactory {
  behavior of "run loop test"

  it should "not fail if complete called" in runLoopTest {
    complete
  } 

  it should "fail if complete not called in desired interval" in {
    val offsetMs = 20
    val startTime = System.currentTimeMillis
    intercept[RunLoopSupport.Timeout] {
      runLoopTestWithTimeout(Interval.milliseconds(offsetMs)){} 
    }

    assert((offsetMs + startTime) <= System.currentTimeMillis)
  }

  it should "be still functioning as a loop" in runLoopTest {

    val mustBeCalled = mock[() => Unit]
    (mustBeCalled.apply _).expects().onCall(complete _)
    

    runLoop.post(mustBeCalled)
  }

  it should "be still functioning as a timer" in runLoopTest {
    val mustBeCalled = mock[() => Unit]
    (mustBeCalled.apply _).expects().onCall(complete _)
    

    runLoop.postDelayed(Interval.milliseconds(20),
                        mustBeCalled)
  } 
} 
