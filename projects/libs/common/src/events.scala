package tas.events

import scala.collection.mutable.ListBuffer
import tas.timers.Timer

object Event {
  def newSync[T]:SimpleEvent[T] = new SyncSimpleEvent[T]
  def newAsync[T](timer:Timer):SimpleEvent[T] = new AsyncSimpleEvent[T](timer)
}

trait Subscription[Subscriber] {
  def +=(subscriber:Subscriber):Unit
  def -=(subscriber:Subscriber):Unit
}

trait Event[T] extends Subscription[T=>Unit]{
  def +=(subscriber:T=>Unit):Unit
  def -=(subscriber:T=>Unit):Unit
}

trait BaseSubscription[Subscriber] extends Subscription[Subscriber] {
  def run(action:Subscriber=>Unit)
} 

class SyncSubscription[Subscriber] extends Subscription[Subscriber] {

  private var _subscribers = new ListBuffer[Subscriber]()
  private val _subscribersReceivingEvents = new ListBuffer[ListBuffer[Subscriber]]()

  override def +=(subscriber:Subscriber):Unit = {
      _subscribers = _subscribers.:+(subscriber)
    }

  override def -=(subscriber:Subscriber):Unit = {
      if (_subscribers.indexOf(subscriber) < 0) throw new Error("Trying to unsubscribe not subscribed subscriber.")

      _subscribers = _subscribers - subscriber

      _subscribersReceivingEvents.foreach(_.-=(subscriber))
    }

  final def run(action:Subscriber=>Unit):Unit = {
    if (_subscribers.isEmpty) return

    if (_subscribers.size == 1) action(_subscribers.head)
    else {

      val willGetThisEvent = _subscribers

      try {
        _subscribersReceivingEvents += willGetThisEvent

        willGetThisEvent.foreach(action(_))
      } finally {
        _subscribersReceivingEvents -= willGetThisEvent
      }
    }

  }
}

class AsyncSubscription[Subscriber](timer:Timer) extends Subscription[Subscriber] {

  private class AsyncEventPass(private var subscribers:List[Subscriber], action:Subscriber=>Unit) {
    def apply(action:Subscriber=>Unit) {
      subscribers.foreach(action(_))
    }

    def remove(subscriber:Subscriber) = {
      subscribers = subscribers.filterNot(_ == subscriber)
    } 
  } 

  private val _currentSubscribers = new ListBuffer[Subscriber]()
  private val _asyncPasses = new ListBuffer[AsyncEventPass]

  override def +=(subscriber:Subscriber):Unit = _currentSubscribers += subscriber

  override def -=(subscriber:Subscriber):Unit = {
    val subscribed = _currentSubscribers.indexOf(subscriber) >= 0

    if (subscribed) {
      _currentSubscribers -= subscriber
      _asyncPasses.foreach(_.remove(subscriber))
    } 
  }

  final def run(action:Subscriber=>Unit):Unit = {
    if (_currentSubscribers.isEmpty) return

    val pass = new AsyncEventPass(_currentSubscribers.toList, action)
  
    _asyncPasses += pass

    timer.run {
      pass.apply(action)
      _asyncPasses -= pass
    }
  }
}

trait SimpleEvent[T] extends Event[T] {
  def << (value:T)
} 


class SyncSimpleEvent[T] protected[events] extends SyncSubscription[T=>Unit] with SimpleEvent[T] {
  def <<(value:T):Unit = run(_(value))
}

class AsyncSimpleEvent[T] protected[events] (timer:Timer) extends AsyncSubscription[T=>Unit](timer) with SimpleEvent[T] {
  def <<(value:T):Unit = run(_(value))
}


class SyncCallSubscription extends SyncSubscription[()=>Unit] {
  def apply() = run(_())
}
