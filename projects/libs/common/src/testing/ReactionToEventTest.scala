package tas.testing

import tas.timers.JustNowFakeTimer

import scala.collection.mutable.ListBuffer

import tas.types.Time


object ReactionToEventTest {
  private class EventSlot(val triggerAction:()=>Unit) extends (()=>Unit){
    private val expectationSetters = new ListBuffer[()=>Unit]

    def addExpectationSetter(setter:()=>Unit) = expectationSetters += setter

    def apply():Unit = {
      // call all expectation setters to setup expectations
      expectationSetters.foreach(_())

      // trigger action, to make strategy react on it
      triggerAction()
    }
  }
}

trait ReactionToEventTest extends DelayedInit {

  import ReactionToEventTest.EventSlot

  val timer = new JustNowFakeTimer

  private var _latestActionSlot:EventSlot = null

  def nextEventAt(time:Time)(action: =>Unit) = {
    _latestActionSlot = new EventSlot(() => {
                                        action
                                      } )

    timer.callAt(time,
                 _latestActionSlot)
  }

  def setupExpectation(action: =>Unit) = {
    _latestActionSlot.addExpectationSetter(() => action)
  }

  def delayedInit(body: =>Unit) = {
    body

    timer.loop()
  }
}
