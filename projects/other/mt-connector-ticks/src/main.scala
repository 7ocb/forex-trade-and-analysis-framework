
import tas.sources.ticks.TicksFromRemotePeriods
import tas.sources.periods.Ticks2Periods
import tas.timers.RealTimeTimer
import tas.timers.Timer
import tas.concurrency.RunLoop

import tas.hub.clients.HubConnectionTicksSource
import tas.service.AddressByName

import tas.output.logger.{
  ScreenLogger,
  PrefixTimerTime
}


import tas.types.Time
import tas.types.Interval

import tas.readers.PeriodsSequence

import java.net.URL
import java.io.InputStream

object Run extends App {

  val runLoop = new RunLoop

  val rtTimer = new RealTimeTimer(runLoop)
  val logger = new PrefixTimerTime(rtTimer,
                                   ScreenLogger)
 
  
  val ticker = new HubConnectionTicksSource(runLoop,
                                            logger,
                                            new AddressByName("127.0.0.1", 9101),
                                            "audusd-mt-finam-limited")

  ticker.tickEvent += (tick => {
    println("" + rtTimer.currentTime + ", tick: " + tick.price)
  })

  val period = Interval.minutes(10)

  val periodComposer = new Ticks2Periods(rtTimer,
                                         period,
                                         period.findNextStartInDay(rtTimer.currentTime))

  periodComposer.bindTo(ticker.tickEvent)

  periodComposer.periodCompleted += (period => {
    println("" + rtTimer.currentTime + ", completed period: " + period)
  } )
  

  runLoop()
} 
