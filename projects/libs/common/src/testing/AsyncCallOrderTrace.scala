package tas.testing

import tas.timers.JustNowFakeTimer
import tas.timers.Timer

trait AsyncCallOrderTrace {

  
  private class CallOrderTest {
    private var nextCallOrder = 0
    private var maxCallOrder = 0

    def call(currentCallOrder:List[Int]) = {
      if (! (currentCallOrder contains nextCallOrder)) throw new Error("Invalid call order, expected " + currentCallOrder + " but get " + nextCallOrder)

      nextCallOrder += 1

      maxCallOrder = scala.math.max(maxCallOrder,
                                    currentCallOrder.max)
    }

    def check = {
      if (((maxCallOrder + 1) != nextCallOrder)) throw new Error("Seems not all calls performed")
    } 
  } 
  
  private var callOrder = new CallOrderTest

  private def resetCallOrderTest = callOrder = new CallOrderTest

  def calledInOrder(order:Int*) = {
    callOrder.call(List(order:_*))
  }
  
  def shouldNotBeCalled = throw new Error("called something should not be called")
  
  def orderingCalls(action: =>Unit) = {
    resetCallOrderTest
    action
    callOrder.check
  }

  def asyncTest(action: (Timer)=>Unit) {
    JustNowFakeTimer (timer => {
                        orderingCalls {
                          action(timer)
                        }
                      })
  }

} 
