package testing.utils.reactions

import org.scalatest.{
  SuiteMixin,
  FlatSpec
}

import org.scalamock.scalatest.MockFactory

import scala.collection.mutable.ListBuffer

object ExpectationsAfterEventsTests {
  private class EventSlot(val triggerAction:()=>Unit) extends (()=>Unit) {
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

trait ExpectationsAfterEventsTests extends InitAndRunTests { this: (FlatSpec with MockFactory) =>

  import ExpectationsAfterEventsTests.EventSlot

  trait Test extends super.Test {

    private var _latestActionSlot:EventSlot = null

    protected final def newEventSlot(action:()=>Unit):(()=>Unit) = {
      val eventSlot = new EventSlot(action)
      _latestActionSlot = eventSlot
      eventSlot
    }

    protected final def setupExpectation(action: =>Unit) = {
      _latestActionSlot.addExpectationSetter(() => action)
    }
  }
}
