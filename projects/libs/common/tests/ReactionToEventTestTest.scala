package tests.testing

import tas.types.Time

import org.scalatest.FlatSpec
import org.scalamock.scalatest.MockFactory

import testing.utils.reactions.ReactionToEventTests

class ReactionToEventTestsTest extends FlatSpec with MockFactory with ReactionToEventTests {
  behavior of "ReactionToEventTests"

  it should "not fail if nothing done" in new Test {
  }

  it should "call event actions after init code" in new Test {

      val firstCalled = mock[()=>Unit]
      val secondCalled = mock[()=>Unit]
      val thirdCalled = mock[()=>Unit]

      inSequence {
        (firstCalled.apply _).expects()

        (secondCalled.apply _).expects()

        (thirdCalled.apply _).expects()
      }

      firstCalled()

      nextEventAt(Time.fromCalendar(2000, 0, 1)) {
        thirdCalled()
      }

      secondCalled()

  }

  it should "call event expectations before event itself" in new Test {
    val firstCalled = mock[()=>Unit]
    val secondCalled = mock[()=>Unit]
    val thirdCalled = mock[()=>Unit]

    inSequence {
      (firstCalled.apply _).expects()

      (secondCalled.apply _).expects()

      (thirdCalled.apply _).expects()
    }

    firstCalled()

    nextEventAt(Time.fromCalendar(2000, 0, 1)) {
      thirdCalled()
    }

    setupExpectation {
      secondCalled()
    }
  }

  it should "throw exception in case if next event earlier than previous" in new Test {
    nextEventAt(Time.fromCalendar(2000, 0, 2)) {}

    intercept[ReactionToEventTests.SettingEventToPast] {
      nextEventAt(Time.fromCalendar(2000, 0, 1)) {}
    }
  }


  it should "call expectation setters in order, but before corresponding event" in new Test {
    val checkpoints = List.fill(8)(mock[()=>Unit])

    inSequence {
      checkpoints.foreach( cp => (cp.apply _).expects() )
    }

    checkpoints(0)()

    nextEventAt(Time.fromCalendar(2000, 0, 1)) {
      checkpoints(5)()
    }

    setupExpectation {
      checkpoints(3)()
    }

    setupExpectation {
      checkpoints(4)()
    }

    checkpoints(1)()

    nextEventAt(Time.fromCalendar(2000, 0, 2)) {
      checkpoints(7)()
    }

    setupExpectation {
      checkpoints(6)()
    }

    checkpoints(2)()

  }
}
