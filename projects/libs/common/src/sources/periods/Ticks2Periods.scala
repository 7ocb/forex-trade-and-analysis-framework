package tas.sources.periods


import tas.events.{Event, SyncSubscription}

import tas.types.{
  Time,
  Interval,
  Fraction,
  Price,
  Period
}

import tas.timers.Timer



class Ticks2Periods(timer:Timer,
                    eventToBindTo:Event[Price],
                    interval:Interval,
                    startAt:Time,
                    endAt:Option[Time] = None) extends PeriodSource {
  
  def this(timer:Timer,
           eventToBindTo:Event[Price],
           interval:Interval,
           startAt:Time,
           endAt:Time) = this(timer,
                              eventToBindTo,
                              interval,
                              startAt,
                              Some(endAt))

  private var _startTime = startAt
  
  private val _periodCompleted = Event.newSync[Period]
  private val _periodUpdated = Event.newSync[Period]
  private val _emptyPeriodEnded = Event.newSync[Unit]

  private var _period:Period = null

  submitCompletePeriod()
  
  override def periodCompleted:Event[Period] = _periodCompleted
  override def periodUpdated:Event[Period] = _periodUpdated
  override def emptyPeriodEnded:Event[Unit] = _emptyPeriodEnded

  eventToBindTo += onTick
  
  private def onTick(price:Price):Unit = {

    if (timer.currentTime < startAt) return
  
    if (_period == null) _period = new Period(price, price, price, price, _startTime)
    else {
      import scala.math._
            
      _period = new Period(_period.priceOpen,
                           price,
                           _period.priceMin.min(price),
                           _period.priceMax.max(price),
                           _period.time)
    } 

    _periodUpdated << _period
  }

  private def completePeriod():Unit = {
    if (_period != null) {
      _periodCompleted << _period
    } else {
      // TODO: this is temporary solution until event mechanics will be
      // refactored to make it more consistent.
      _emptyPeriodEnded << null
    }
    _period = null
    
    submitCompletePeriod()
  }

  private def submitCompletePeriod() {
    val completeTime = _startTime + interval
    
    if (endAt.isEmpty || completeTime <= endAt.get) {

      timer.at(completeTime) {
        _startTime = completeTime
        completePeriod()
      }
      
    } 
  } 
} 
