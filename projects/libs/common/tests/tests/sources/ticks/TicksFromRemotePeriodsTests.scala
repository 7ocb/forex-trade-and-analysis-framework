package tests.sources.ticks

import tas.types.Fraction.int2Fraction

import org.scalatest.FlatSpec
import org.scalamock.scalatest.MockFactory

import tas.types.{
  PeriodBid,
  Price,
  Fraction
}

import tas.timers.Timer
import tas.types.Time

import tas.sources.ticks.TicksFromRemotePeriods
import tas.sources.ticks.TicksFromRemotePeriods.PeriodsGetter
import tas.sources.ticks.TicksFromRemotePeriods.ResultHandler

import tas.types.Interval

import tas.testing.TimerSupport

class TicksFromRemotePeriodsTests extends FlatSpec with MockFactory with TimerSupport {

  {
    behavior of "TicksFromRemotePeriods"
    
    it should "set handler, request data, dispatch ticks" in timerRun {
      val getter = mock[PeriodsGetter]

      var internalHandler:ResultHandler = null

      val tickListener = mock[Price => Unit]

      var ticker:TicksFromRemotePeriods = null

      val updateInterval = Interval.seconds(15)
      
      inSequence {
        (getter.setResultHandler _).expects(*).onCall((handler:ResultHandler) => {
          internalHandler = handler

          assert(internalHandler != null)

          info("call setResultHandler once to register result handler in getter")
        } )

        (getter.request _).expects(None).onCall((time:Option[Time]) => {
          info("call request without time to request periods containing current period")
          timer.run {

            // it must be independent of time of periods
            internalHandler(List(new PeriodBid(1, 2, 0, 3, Time.fromCalendar(2010, 10, 10, 10, 10, 0)),
                                 new PeriodBid(2, 3, 1, 4, Time.fromCalendar(2010, 10, 10, 10, 11, 0)),
                                 new PeriodBid(3, 4, 2, 5, Time.fromCalendar(2010, 10, 10, 10, 12, 0))))
          } 
        } )

        val startTime = timer.currentTime
        
        // and it should dispatch four ticks from updating period
        val tickStep = updateInterval / 4
        (tickListener.apply _).expects(new Price(3, 4)).onCall((_:Price) => {
          assert(timer.currentTime === startTime)
        } )
        
        (tickListener.apply _).expects(new Price(5, 6)).onCall((_:Price) => {
          assert(timer.currentTime === (startTime + tickStep))
        } )
        (tickListener.apply _).expects(new Price(2, 3)).onCall((_:Price) => {
          assert(timer.currentTime === (startTime + tickStep * 2))
        } )
        (tickListener.apply _).expects(new Price(4, 5)).onCall((_:Price) => {
          assert(timer.currentTime === (startTime + tickStep * 3))
        } )

        val requestOffset = Interval.milliseconds(100)
        
        // in next request it should specify start time of last period, as
        // only last period it interested in
        (getter.request _).expects(Some(Time.fromCalendar(2010, 10, 10, 10, 12, 0))).onCall((time:Option[Time]) => {
          info("and perform next request after update interval")
          assert(timer.currentTime == (startTime + updateInterval))

          timer.after(requestOffset) {
            internalHandler(List(new PeriodBid(3, 4, 2, 5, Time.fromCalendar(2010, 10, 10, 10, 12, 0))))
          } 
        } )

        (getter.request _).expects(Some(Time.fromCalendar(2010, 10, 10, 10, 12, 0))).onCall((time:Option[Time]) => {
          assert(timer.currentTime == (startTime + updateInterval * 2 + requestOffset))

          timer.after(requestOffset) {
            info("it should dispatch end and min/max if changed")
            internalHandler(List(new PeriodBid(3, 5, 1, 6, Time.fromCalendar(2010, 10, 10, 10, 12, 0))))
          }
        } )

        (tickListener.apply _).expects(new Price(6, 7))
        (tickListener.apply _).expects(new Price(1, 2))
        (tickListener.apply _).expects(new Price(5, 6))

        (getter.request _).expects(Some(Time.fromCalendar(2010, 10, 10, 10, 12, 0))).onCall((time:Option[Time]) => {
          timer.after(requestOffset) {
            info("on new period, dispatch end (if no min/max change) for closed and all for new")

            internalHandler(List(new PeriodBid(3, 2, 1, 6, Time.fromCalendar(2010, 10, 10, 10, 12, 0)),
                                 new PeriodBid(1, 4, 0, 6, Time.fromCalendar(2010, 10, 10, 10, 13, 0))))
          }
        } )

        (tickListener.apply _).expects(new Price(2, 3))
        (tickListener.apply _).expects(new Price(1, 2))
        (tickListener.apply _).expects(new Price(6, 7))
        (tickListener.apply _).expects(new Price(0, 1))
        (tickListener.apply _).expects(new Price(4, 5))

        (getter.request _).expects(Some(Time.fromCalendar(2010, 10, 10, 10, 13, 0))).onCall((time:Option[Time]) => {
          timer.after(requestOffset) {
            info("on several new periods, dispatch end (if no min/max change) for closed and all for each new")

            internalHandler(List(new PeriodBid(1, 4, 0, 6, Time.fromCalendar(2010, 10, 10, 10, 13, 0)),
                                 new PeriodBid(3, 6, 2, 8, Time.fromCalendar(2010, 10, 10, 10, 14, 0)),
                                 new PeriodBid(2, 5, 1, 7, Time.fromCalendar(2010, 10, 10, 10, 15, 0))))
          }
        } )

        (tickListener.apply _).expects(new Price(4, 5))
        (tickListener.apply _).expects(new Price(3, 4))
        (tickListener.apply _).expects(new Price(8, 9))
        (tickListener.apply _).expects(new Price(2, 3))
        (tickListener.apply _).expects(new Price(6, 7))
        (tickListener.apply _).expects(new Price(2, 3))
        (tickListener.apply _).expects(new Price(7, 8))
        (tickListener.apply _).expects(new Price(1, 2))
        (tickListener.apply _).expects(new Price(5, 6))

        (getter.request _).expects(Some(Time.fromCalendar(2010, 10, 10, 10, 15, 0))).onCall((time:Option[Time]) => {
          info("on new periods, if old changed min/max, it should be dispatched before new")
          timer.run {
            internalHandler(List(new PeriodBid(1, 4, 0, 8, Time.fromCalendar(2010, 10, 10, 10, 15, 0)),
                                 new PeriodBid(2, 5, 1, 6, Time.fromCalendar(2010, 10, 10, 10, 16, 0))))
          } 
        } )

        (tickListener.apply _).expects(new Price(8, 9)) 
        (tickListener.apply _).expects(new Price(0, 1))
        (tickListener.apply _).expects(new Price(4, 5))
        (tickListener.apply _).expects(new Price(2, 3))
        (tickListener.apply _).expects(new Price(6, 7))
        (tickListener.apply _).expects(new Price(1, 2))
        (tickListener.apply _).expects(new Price(5, 6))

        (getter.request _).expects(Some(Time.fromCalendar(2010, 10, 10, 10, 16, 0)))
          .onCall((time:Option[Time]) => {
                    info("on new periods, if old changed min/max, it should be dispatched before new")
                    timer.run {
                      internalHandler(List[PeriodBid]())
                    }
                  })

        (getter.request _).expects(Some(Time.fromCalendar(2010, 10, 10, 10, 16, 0)))
        
      }

      ticker = new TicksFromRemotePeriods(timer, getter, updateInterval, Fraction(1))
      ticker.tickEvent += tickListener
    }
  } 

} 
