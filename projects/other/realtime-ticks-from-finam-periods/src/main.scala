
import tas.sources.ticks.TicksFromRemotePeriods
import tas.sources.periods.Ticks2Periods
import tas.timers.RealTimeTimer
import tas.timers.Timer
import tas.concurrency.RunLoop

import tas.output.logger.{
  ScreenLogger,
  PrefixTimerTime
}


import tas.types.Time
import tas.types.Interval

import tas.readers.PeriodsSequence

import java.net.URL
import java.io.InputStream

import tas.sources.finam.FinamUrl
import tas.sources.finam.Parameters
import tas.sources.finam.FinamUrlFactory

import tas.sources.ticks.GetterDownloader

object Run extends App {

  val runLoop = new RunLoop

  val rtTimer = new RealTimeTimer(runLoop)
  val logger = new PrefixTimerTime(rtTimer,
                                   ScreenLogger)
 
  
  val ticker = new TicksFromRemotePeriods(rtTimer,
                                          new GetterDownloader(runLoop,
                                                               new FinamUrlFactory(rtTimer),
                                                               logger),
                                          Interval.seconds(10))

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
