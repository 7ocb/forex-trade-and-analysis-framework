package testing.timers

import org.scalamock.scalatest.MockFactory
import org.scalatest.FlatSpec
import tas.events.{
  Event,
  SyncCallSubscription
}

import tas.timers.Timer
import tas.timers.JustNowFakeTimer
import tas.testing.AsyncCallOrderTrace

import scala.collection.mutable.ListBuffer

class EventsTest extends FlatSpec with MockFactory with AsyncCallOrderTrace {
  behavior of "sync event"

  it should "dispatch event at a time of passing" in {

    val sync = Event.newSync[Int]

    val input = ListBuffer(1, 2, 3)
    val output = ListBuffer[Int]()

    sync += (output += _)    
    input.foreach(sync << _)

    assert(output === input)
  }

  it should "dispatch nothing to listener was not subscribed when happened" in {
    val subscription = new SyncCallSubscription()

    val shouldBeCalled = mock[()=>Unit]
    val shouldNotBeCalled = mock[()=>Unit]

    (shouldBeCalled.apply _).expects()
      .onCall(() => {
                subscription += shouldNotBeCalled
              })

    subscription += shouldBeCalled

    subscription()
  }

  it should "dispatch nothing to listener was not subscribed when happened\n  (>2 listeners)" in {
    val subscription = new SyncCallSubscription()

    val shouldBeCalled = mock[()=>Unit]
    val shouldBeCalledToo = mock[()=>Unit]
    val shouldNotBeCalled = mock[()=>Unit]

    (shouldBeCalled.apply _).expects()
      .onCall(() => {
                subscription += shouldNotBeCalled
              })

    (shouldBeCalledToo.apply _).expects()

    subscription += shouldBeCalled
    subscription += shouldBeCalledToo

    subscription()
  }

  it should "dispatch nothing to listener was not subscribed while dispatching" in {
    val subscription = new SyncCallSubscription()

    val shouldBeCalled = mock[()=>Unit]
    val shouldNotBeCalled = mock[()=>Unit]

    (shouldBeCalled.apply _).expects()
      .onCall(() => {
                subscription -= shouldNotBeCalled
              })

    subscription += shouldBeCalled
    subscription += shouldNotBeCalled

    subscription()
  }

  it should "dispatch nothing to listener was not subscribed while dispatching\n   with > 2 listeners" in {
    val subscription = new SyncCallSubscription()

    val shouldBeCalled = mock[()=>Unit]
    val shouldBeCalledToo = mock[()=>Unit]
    val shouldNotBeCalled = mock[()=>Unit]

    (shouldBeCalled.apply _).expects()
      .onCall(() => {
                subscription -= shouldNotBeCalled
              })

    (shouldBeCalledToo.apply _).expects()

    subscription += shouldBeCalled
    subscription += shouldBeCalledToo

    subscription += shouldNotBeCalled

    subscription()
  }

  it should "dispatch nothing to listener was resubscribed while dispatching" in {
    val subscription = new SyncCallSubscription()

    val shouldBeCalled = mock[()=>Unit]
    val shouldNotBeCalled = mock[()=>Unit]

    (shouldBeCalled.apply _).expects()
      .onCall(() => {
                subscription -= shouldNotBeCalled
                subscription += shouldNotBeCalled
              })

    subscription += shouldBeCalled
    subscription += shouldNotBeCalled

    subscription()
  }

  it should "dispatch nothing to listener was resubscribed while dispatching\n   with > 2 listeners" in {
    val subscription = new SyncCallSubscription()

    val shouldBeCalled = mock[()=>Unit]
    val shouldBeCalledToo = mock[()=>Unit]
    val shouldNotBeCalled = mock[()=>Unit]

    (shouldBeCalled.apply _).expects()
      .onCall(() => {
                subscription -= shouldNotBeCalled
                subscription += shouldNotBeCalled
              })

    (shouldBeCalledToo.apply _).expects()

    subscription += shouldBeCalled
    subscription += shouldBeCalledToo
    subscription += shouldNotBeCalled

    subscription()
  }

  behavior of "async event"

  it should "not dispatch event before loop, but in loop" in {
    val timer = new JustNowFakeTimer
    
    val async = Event.newAsync[Int](timer)

    val input = ListBuffer(1, 2, 3)
    val output = ListBuffer[Int]()

    async += (output += _)    
    input.foreach(async << _)

    assert(output === ListBuffer[Int]())
    
    timer.loop
    
    assert(output === input)
  }

  it should "not call listener bound after value change but before dispatching" in asyncTest (
    timer => {
      val async = Event.newAsync[Int](timer)

      async += (v => calledInOrder(1))

      async << 1

      async += (v => shouldNotBeCalled)

      calledInOrder(0)
    })

  it should "not call listener if it removed after change but before dispatching" in asyncTest (
    timer => {
      val async = Event.newAsync[Int](timer)

      val handler:Int=>Unit = (v => shouldNotBeCalled)

      async += handler
      async += (v => calledInOrder(1))
      async << 1
      async -= handler

      calledInOrder(0)
    })
  
}   
