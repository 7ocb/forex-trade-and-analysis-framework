package testing.utils.reactions

import tas.timers.JustNowFakeTimer

import scala.collection.mutable.ListBuffer

import tas.types.Time

import org.scalatest.{
  SuiteMixin,
  FlatSpec
}

import org.scalamock.scalatest.MockFactory

object ReactionToEventTests {
  class SettingEventToPast extends Exception("Trying to set event to the past")
}


trait ReactionToEventTests extends ExpectationsAfterEventsTests { this: (FlatSpec with MockFactory) =>

  import ReactionToEventTests._

  trait Test extends super.Test {

    val timer = new JustNowFakeTimer

    private var _latestEventTime:Time = null

    def nextEventAt(time:Time)(action: =>Unit) = {
      val runEventSlot = newEventSlot(() => {
                                        action
                                      } )

      if (_latestEventTime != null
            && _latestEventTime > time) throw new SettingEventToPast

      _latestEventTime = time

      timer.callAt(time, runEventSlot)
    }

    override def run() = {
      timer.loop
    }
  }


}
