
import scala.collection.mutable.ListBuffer
import scala.util.Random

import tas.sources.ticks.TicksFromRemotePeriods
import tas.sources.periods.Ticks2Periods
import tas.timers.RealTimeTimer
import tas.timers.Timer
import tas.concurrency.RunLoop

import tas.hub.clients.{
  HubConnectionTicksSource,
  HubConnectionTradeBackend
}
import tas.service.AddressByName

import tas.output.logger.{
  ScreenLogger,
  FileLogger,
  Logger
}


import tas.types.{
  Time,
  Interval,
  Fraction
}

import tas.readers.PeriodsSequence

import java.net.URL
import java.io.InputStream

import tas.ppdesimple.strategy.{
  PpdeSimpleModified => Strategy,
  AllowedBoth,
  SameIfNoDirection
}
import tas.ppdesimple.strategy.PpdeSimpleModified.{
  Config => StrategyConfig,
  Stops
}

import tas.strategies.activeness.DefaultActiveness

import tas.strategies.running.{
  WithRunLoop,
  WithRealTimeTimer,
  WithTimedLogger
}

import tas.trading.simulation.TradingSimulation
import tas.trading.TradeBackend

import tas.trading.simulation.config.{
  Config => SimulatorConfig,
  NoComission,
  OpenAndCloseBy
}

import tas.trading.simulation.config.limits.{
  Limits,
  IndependentStops
}



object RunTester extends App with WithRunLoop with WithRealTimeTimer with WithTimedLogger {

  val strategies = new ListBuffer[Strategy]

  def regenerateStops() = {

    def randomDistance = (Fraction(Random.nextLong % 9) / 10000).abs + Fraction("0.00001")

    val stops = new Stops(randomDistance,
                          randomDistance,
                          randomDistance)

    timedLogger.log("new stops: ", stops)

    strategies.foreach(_.stopsConfig = stops)
  }

  val hubAddress = new AddressByName("127.0.0.1", 9101)
  val hubKey = "eurusd-mt-finam-limited"

  def logFile(name:String) = new FileLogger(name, FileLogger.PlainReopening)

  val logger = logFile("main.log")
  


  
  val ticker = new HubConnectionTicksSource(runLoop,
                                            timedLogger,
                                            hubAddress,
                                            hubKey)

  val ticksLogger = timed(logFile("processing-ticks.log"))

  // log ticks about to being processed
  ticker.tickEvent += (price => ticksLogger.log("tick: " + price))

  case class Sim(logger:Logger, tradeBackend:TradeBackend)

  def sim(logName:String,
          limits:Limits) = {
    val logger = timed(logFile(logName))
    Sim(logger,
        new TradingSimulation(new SimulatorConfig("100",
                                                  "5000",
                                                  OpenAndCloseBy.CurrentPrice,
                                                  limits,
                                                  NoComission),
                              timer,
                              logger,
                              ticker.tickEvent))
  }

  val independentStopsSim =
    sim("simulation-independent-stops.log",
        new IndependentStops("0.0005",
                             "0.0005",
                             "0.0005",
                             freezeDistance = Fraction.ZERO))

  val mtLikeStopsSim =
    sim("simulation-mt-like-stops.log",
        new IndependentStops("0.0005",
                             "0.0005",
                             "0.0005",
                             freezeDistance = Fraction.ZERO))

  val realTradeLogger = timed(logFile("trading.log"))

  val realTrade = new HubConnectionTradeBackend(runLoop,
                                                realTradeLogger,
                                                hubAddress,
                                                hubKey)

  val period = Interval.minutes(5)

  val periodComposer = new Ticks2Periods(timer,
                                         ticker.tickEvent,
                                         period,
                                         period.findNextStartInDay(timer.currentTime))

  // log completed periods
  periodComposer.periodCompleted += {
                                       period =>
    timedLogger.log("completed period: " + period)
    regenerateStops()
  }



  val activenessCondition = new DefaultActiveness(timer,
                                                  timer.currentTime)
  

  def createStrategy(loggerToUse:Logger,
                     trading:TradeBackend):Strategy =
    new Strategy(timer,
                 new StrategyConfig(activenessCondition,
                                    1000,
                                    SameIfNoDirection,
                                    "0.0001",
                                    1,
                                    "0.001",
                                    "0",
                                    AllowedBoth),
                 null,
                 new Strategy.Context {
                   def tradeBackend = trading
                   def periodsSource = periodComposer
                   def logger = loggerToUse
                 } )

  def createStrategy(sim:Sim):Strategy =
    createStrategy(sim.logger, sim.tradeBackend)

  strategies += createStrategy(realTradeLogger, realTrade)
  strategies += createStrategy(independentStopsSim)
  strategies += createStrategy(mtLikeStopsSim)

  regenerateStops()

  runLoop()

}
