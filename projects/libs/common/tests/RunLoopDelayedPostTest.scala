package testing.concurrency

import org.scalatest.FlatSpec

import java.lang.management.ManagementFactory
import java.lang.management.ThreadMXBean

import tas.concurrency.RunLoop

import tas.types.Interval

class RunLoopDelayedPostTest extends FlatSpec {
  behavior of "run loop delayed post"

  it should "post call on run loop thread and end thread on exit" in {
    val f = new RunLoop

    def threadId = Thread.currentThread.getId
    
    val runLoopThreadId = threadId

    f.postDelayed(Interval.milliseconds(1),
                  () => {
                    assert(runLoopThreadId == threadId)
                    f.terminate()
                  } )
    
    f()
  }

  class Delay(val interval:Interval, val targetMsec:Long) {
    def check = assert(targetMsec <= System.currentTimeMillis)
  }
  
  def delayInfo(delayMsec:Long) = new Delay(Interval.milliseconds(delayMsec),
                                            System.currentTimeMillis + delayMsec)
  
  it should "delay task at least at specified interval" in {
    val d = delayInfo(30)

    val f = new RunLoop

    f.postDelayed(d.interval,
                  () => {
                    d.check
                    f.terminate()
                  } )

    f()
  }

  it should "maintain tasks in queued order" in {
    val runs = List(delayInfo(20),
                    delayInfo(30),
                    delayInfo(40))

    val f = new RunLoop

    var callOrder = 0
    
    f.postDelayed(runs(0).interval,
                  () => {
                    runs(0).check
                    assert(callOrder === 0)
                    callOrder += 1
                  } )

    f.postDelayed(runs(1).interval,
                  () => {
                    runs(1).check
                    assert(callOrder === 1)
                    callOrder += 1
                  } )
    
    f.postDelayed(runs(2).interval,
                  () => {
                    runs(2).check
                    assert(callOrder === 2)
                    callOrder += 1
                    f.terminate()
                  } )

    f()
  }

  it should "keep delay in reasonable offset of requested" in {
    val offset = 40
    val d = delayInfo(offset)
    val f = new RunLoop

    f.postDelayed(d.interval,
                  () => {
                    d.check

                    assert((d.targetMsec + 40) > System.currentTimeMillis)
                    
                    f.terminate()
                  } )

    f()
  }

  it should "not call cancelled task" in {
    val f = new RunLoop

    f.postDelayed(Interval.milliseconds(20),
                  () => {
                    throw new Error("Must not be called")
                  } ).cancel()
    
    f.postDelayed(Interval.milliseconds(30),
                  () => {
                    f.terminate()
                  } )

    f()
  }


}
