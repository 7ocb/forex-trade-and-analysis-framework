
import tas.timers.JustNowFakeTimer

import java.io.File

import tas.output.logger.{PrefixTimerTime, FileLogger}
import tas.types.{Interval, Period, Fraction, Time}

import tas.readers.TicksFileMetrics
import tas.sources.ticks.TicksFromSequence

import tas.trading.simulation.TradingSimulation

import tas.sources.periods.Ticks2Periods

import tas.input.format.ticks.metatrader.MetatraderExportedTicks
import tas.input.format.ticks.bincache.TicksBinaryCache

import tas.input.Sequence

import tas.previousdaydirection.strategy.{
  Strategy,
  AllowedTradeTypes,
  AllowedOnlyBuys,
  AllowedBoth
}

object MultistrategyTest extends App {
  // val ticksFileName = "/home/elk/work/tas/data/duka-eurusd-ticks-2014-01-01---2014-02-06-from-periods-42809.txt"
  // dukas-eurusd-ticks-2012-01-01---2012-12-31-from-periods-2859.txt

  // val ticksFileName = "/home/elk/work/tas/data/duka-eurusd-ticks-2013-01-01---2013-12-31-from-periods-83029.txt"
  // val ticksFileName = "/home/elk/work/tas/data/dukas-eurusd-ticks-2012-01-01---2012-12-31-from-periods-2859.txt"  

  // val ticksFileName = "/home/elk/work/tas/data/duka-eurusd-ticks-2012-01-01---2012-12-31-from-periods-5621.txt"
  val ticksFileName = "/home/elk/work/tas/data/duka-eurusd-ticks-2014-01-01---2014-02-06-from-periods-84933.txt"

  val timer = new JustNowFakeTimer

  val fileLogger = new FileLogger("multistrategy.log.gz")

  val timedLogger = new PrefixTimerTime(timer, fileLogger)





  val inputMetrics = TicksFileMetrics.fromFile(ticksFileName)

  val activenessCondition = new ActivenessCondition(timer,
                                                    inputMetrics.firstTickTime)


  val ticksSource = new TicksFromSequence(timer,
                                          Sequence.fromFile(new File(ticksFileName),
                                                            MetatraderExportedTicks,
                                                            TicksBinaryCache))

  val trading = new TradingSimulation(new TradingSimulation.Configuration(TradingSimulation.Pessimistic,
                                                                          stopMinimalDistance = Fraction("0.0005"),
                                                                          tpMinimalDistance = Fraction("0.0005"),
                                                                          delayMinimalDistance = Fraction("0.0005"),
                                                                          freezeDistance = Fraction("0.0005")),
                                      timer,
                                      "200",
                                      "400",
                                      timedLogger,
                                      "0.0003",
                                      ticksSource.tickEvent)


  timer.callAt(inputMetrics.lastTickTime,
               timer.stop)

  def postNextStatus(time:Time):Unit = {

    if (time < inputMetrics.lastTickTime) {

      timer.callAt(time + Interval.minutes(30),
                   () => {
                     fileLogger.log("--- equity: ", trading.equity)
                     postNextStatus(timer.currentTime)
                   } )
    }
  }

  postNextStatus(inputMetrics.firstTickTime)

  def createStrategy(period:Interval,
                     periodStartShift:Interval,
                     orderDelayDistance:Fraction,
                     tpFactor:Fraction,
                     firstStopDistance:Fraction,
                     dontOpenTradeIfStopLessThan:Fraction,
                     stopDistanceUpperLimit:Fraction,
                     periodsCountToDetectSerie:Int,
                     oneTradeRiskFactor:Fraction,
                     maxTradesKeptOpened:Int,
                     tradeComissionFactor:Fraction,
                     directionDetectionTolerance:Fraction,
                     allowedTradeTypes:AllowedTradeTypes) {

    val composer = new Ticks2Periods(timer,
                                     period,
                                     Strategy.nextPeriodStartTime(inputMetrics.firstTickTime,
                                                                  period,
                                                                  periodStartShift))

    composer.bindTo(ticksSource.tickEvent)

    new Strategy(timer,
                 new Strategy.Config(activenessCondition,
                                     orderDelayDistance,
                                     tpFactor,
                                     new Strategy.StopsSettings(firstStopDistance,
                                                                dontOpenTradeIfStopLessThan,
                                                                stopDistanceUpperLimit),
                                     periodsCountToDetectSerie,
                                     oneTradeRiskFactor,
                                     maxTradesKeptOpened,
                                     tradeComissionFactor,
                                     Config.tradeValueGranularity,
                                     directionDetectionTolerance,
                                     allowedTradeTypes),
                 new Strategy.Context {
                   def tradeBackend = trading
                   def periodsSource = composer
                   def logger = timedLogger
                   def balance = trading.balance
                 } )
  }

  // createStrategy(Interval.minutes(90),
  //                orderDelayDistance = "0.0029",
  //                tpFactor = "0.2",
  //                firstStopDistance = "0.0015",
  //                dontOpenTradeIfStopLessThan = "0.0013",
  //                stopDistanceUpperLimit = "0.01",
  //                periodsCountToDetectSerie = 1,
  //                oneTradeRiskFactor = "0.02",
  //                maxTradesKeptOpened = 3,
  //                tradeComissionFactor = "0.0003",
  //                directionDetectionTolerance = "0.0008",
  //                AllowedOnlyBuys)

  // createStrategy(Interval.minutes(90),
  //                tradeComissionFactor = "0.0003",
  //                oneTradeRiskFactor = "0.02",
  //                maxTradesKeptOpened = 3,
  //                firstStopDistance = "0.0013",
  //                dontOpenTradeIfStopLessThan = "0.0013",
  //                stopDistanceUpperLimit = "0.01",
  //                orderDelayDistance = "0.0029",
  //                periodsCountToDetectSerie = 1,
  //                directionDetectionTolerance = "0.0008",
  //                tpFactor = "0.4",
  //                allowedTradeTypes = AllowedOnlyBuys)

  // createStrategy(Interval.minutes(90),
  //                tradeComissionFactor = "0.0003",
  //                oneTradeRiskFactor = "0.02",
  //                maxTradesKeptOpened = 3,
  //                firstStopDistance = "0.0017",
  //                dontOpenTradeIfStopLessThan = "0.0013",
  //                stopDistanceUpperLimit = "0.01",
  //                orderDelayDistance = "0.0029",
  //                periodsCountToDetectSerie = 1,
  //                directionDetectionTolerance = "0.0008",
  //                tpFactor = "0.2",
  //                allowedTradeTypes = AllowedOnlyBuys)


  // createStrategy(Interval.minutes(90),
  //                tradeComissionFactor = "0.0003",
  //                oneTradeRiskFactor = "0.02",
  //                maxTradesKeptOpened = 3,
  //                firstStopDistance = "0.0013",
  //                dontOpenTradeIfStopLessThan = "0.0013",
  //                stopDistanceUpperLimit = "0.01",
  //                orderDelayDistance = "0.0027",
  //                periodsCountToDetectSerie = 1,
  //                directionDetectionTolerance = "0.0002",
  //                tpFactor = "0.2",
  //                allowedTradeTypes = AllowedOnlyBuys)

  // createStrategy(Interval.minutes(90),
  //                tradeComissionFactor = "0.0003",
  //                oneTradeRiskFactor = "0.02",
  //                maxTradesKeptOpened = 3,
  //                firstStopDistance = "0.0013",
  //                dontOpenTradeIfStopLessThan = "0.0013",
  //                stopDistanceUpperLimit = "0.01",
  //                orderDelayDistance = "0.0045",
  //                periodsCountToDetectSerie = 1,
  //                directionDetectionTolerance = "0.0002",
  //                tpFactor = "0.7",
  //                allowedTradeTypes = AllowedOnlyBuys)


  // createStrategy(Interval.minutes(90),
  //                tradeComissionFactor = "0.0003",
  //                oneTradeRiskFactor = "0.02",
  //                maxTradesKeptOpened = 3,
  //                firstStopDistance = "0.0015",
  //                dontOpenTradeIfStopLessThan = "0.0013",
  //                stopDistanceUpperLimit = "0.01",
  //                orderDelayDistance = "0.0027",
  //                periodsCountToDetectSerie = 1,
  //                directionDetectionTolerance = "0.0002",
  //                tpFactor = "0.2",
  //                allowedTradeTypes = AllowedOnlyBuys)

  // createStrategy(Interval.minutes(90),
  //                tradeComissionFactor = "0.0003",
  //                oneTradeRiskFactor = "0.02",
  //                maxTradesKeptOpened = 3,
  //                firstStopDistance = "0.0013",
  //                dontOpenTradeIfStopLessThan = "0.0013",
  //                stopDistanceUpperLimit = "0.01",
  //                orderDelayDistance = "0.0029",
  //                periodsCountToDetectSerie = 1,
  //                directionDetectionTolerance = "0.0001",
  //                tpFactor = "0.4",
  //                allowedTradeTypes = AllowedOnlyBuys)


  createStrategy(tradeComissionFactor = "0.0003",
                 oneTradeRiskFactor = "0.03",
                 maxTradesKeptOpened = 3,
                 firstStopDistance = "0.0014",
                 dontOpenTradeIfStopLessThan = "0.0013",
                 stopDistanceUpperLimit = "0.01",
                 orderDelayDistance = "0.0035",
                 periodsCountToDetectSerie = 1,
                 period = Interval.minutes(90),
                 periodStartShift = Interval.minutes(80),
                 directionDetectionTolerance = "0.0002",
                 tpFactor = "0.4",
                 allowedTradeTypes = AllowedBoth)

  createStrategy(tradeComissionFactor = "0.0003",
                 oneTradeRiskFactor = "0.03",
                 maxTradesKeptOpened = 3,
                 firstStopDistance = "0.0017",
                 dontOpenTradeIfStopLessThan = "0.0013",
                 stopDistanceUpperLimit = "0.01",
                 orderDelayDistance = "0.0029",
                 periodsCountToDetectSerie = 1,
                 period = Interval.minutes(90),
                 periodStartShift = Interval.minutes(80),
                 directionDetectionTolerance = "0",
                 tpFactor = "0.2",
                 allowedTradeTypes = AllowedBoth)

  createStrategy(tradeComissionFactor = "0.0003",
                 oneTradeRiskFactor = "0.03",
                 maxTradesKeptOpened = 3,
                 firstStopDistance = "0.0017",
                 dontOpenTradeIfStopLessThan = "0.0013",
                 stopDistanceUpperLimit = "0.01",
                 orderDelayDistance = "0.0032",
                 periodsCountToDetectSerie = 1,
                 period = Interval.minutes(90),
                 periodStartShift = Interval.minutes(60),
                 directionDetectionTolerance = "0",
                 tpFactor = "0.2",
                 allowedTradeTypes = AllowedBoth)

  createStrategy(tradeComissionFactor = "0.0003",
                 oneTradeRiskFactor = "0.03",
                 maxTradesKeptOpened = 3,
                 firstStopDistance = "0.0017",
                 dontOpenTradeIfStopLessThan = "0.0013",
                 stopDistanceUpperLimit = "0.01",
                 orderDelayDistance = "0.0032",
                 periodsCountToDetectSerie = 1,
                 period = Interval.minutes(90),
                 periodStartShift = Interval.minutes(60),
                 directionDetectionTolerance = "0.0002",
                 tpFactor = "0.2",
                 allowedTradeTypes = AllowedBoth)

  createStrategy(tradeComissionFactor = "0.0003",
                 oneTradeRiskFactor = "0.03",
                 maxTradesKeptOpened = 3,
                 firstStopDistance = "0.0017",
                 dontOpenTradeIfStopLessThan = "0.0013",
                 stopDistanceUpperLimit = "0.01",
                 orderDelayDistance = "0.0035",
                 periodsCountToDetectSerie = 1,
                 period = Interval.minutes(90),
                 periodStartShift = Interval.minutes(70),
                 directionDetectionTolerance = "0.0002",
                 tpFactor = "0.2",
                 allowedTradeTypes = AllowedBoth)

  createStrategy(tradeComissionFactor = "0.0003",
                 oneTradeRiskFactor = "0.03",
                 maxTradesKeptOpened = 3,
                 firstStopDistance = "0.002",
                 dontOpenTradeIfStopLessThan = "0.0013",
                 stopDistanceUpperLimit = "0.01",
                 orderDelayDistance = "0.0032",
                 periodsCountToDetectSerie = 1,
                 period = Interval.minutes(90),
                 periodStartShift = Interval.minutes(40),
                 directionDetectionTolerance = "0",
                 tpFactor = "0.2",
                 allowedTradeTypes = AllowedBoth)

  createStrategy(tradeComissionFactor = "0.0003",
                 oneTradeRiskFactor = "0.03",
                 maxTradesKeptOpened = 3,
                 firstStopDistance = "0.002",
                 dontOpenTradeIfStopLessThan = "0.0013",
                 stopDistanceUpperLimit = "0.01",
                 orderDelayDistance = "0.0035",
                 periodsCountToDetectSerie = 1,
                 period = Interval.minutes(90),
                 periodStartShift = Interval.minutes(20),
                 directionDetectionTolerance = "0",
                 tpFactor = "0.2",
                 allowedTradeTypes = AllowedBoth)

  createStrategy(tradeComissionFactor = "0.0003",
                 oneTradeRiskFactor = "0.03",
                 maxTradesKeptOpened = 3,
                 firstStopDistance = "0.002",
                 dontOpenTradeIfStopLessThan = "0.0013",
                 stopDistanceUpperLimit = "0.01",
                 orderDelayDistance = "0.0035",
                 periodsCountToDetectSerie = 1,
                 period = Interval.minutes(90),
                 periodStartShift = Interval.minutes(30),
                 directionDetectionTolerance = "0.0004",
                 tpFactor = "0.2",
                 allowedTradeTypes = AllowedBoth)

  createStrategy(tradeComissionFactor = "0.0003",
                 oneTradeRiskFactor = "0.03",
                 maxTradesKeptOpened = 3,
                 firstStopDistance = "0.0023",
                 dontOpenTradeIfStopLessThan = "0.0013",
                 stopDistanceUpperLimit = "0.01",
                 orderDelayDistance = "0.0032",
                 periodsCountToDetectSerie = 1,
                 period = Interval.minutes(90),
                 periodStartShift = Interval.minutes(60),
                 directionDetectionTolerance = "0.0002",
                 tpFactor = "0.4",
                 allowedTradeTypes = AllowedBoth)

  createStrategy(tradeComissionFactor = "0.0003",
                 oneTradeRiskFactor = "0.03",
                 maxTradesKeptOpened = 3,
                 firstStopDistance = "0.0029",
                 dontOpenTradeIfStopLessThan = "0.0013",
                 stopDistanceUpperLimit = "0.01",
                 orderDelayDistance = "0.0035",
                 periodsCountToDetectSerie = 1,
                 period = Interval.minutes(90),
                 periodStartShift = Interval.minutes(50),
                 directionDetectionTolerance = "0.0004",
                 tpFactor = "0.2",
                 allowedTradeTypes = AllowedBoth)

  createStrategy(tradeComissionFactor = "0.0003",
                 oneTradeRiskFactor = "0.03",
                 maxTradesKeptOpened = 3,
                 firstStopDistance = "0.0032",
                 dontOpenTradeIfStopLessThan = "0.0013",
                 stopDistanceUpperLimit = "0.01",
                 orderDelayDistance = "0.0032",
                 periodsCountToDetectSerie = 1,
                 period = Interval.minutes(90),
                 periodStartShift = Interval.minutes(60),
                 directionDetectionTolerance = "0.0004",
                 tpFactor = "0.2",
                 allowedTradeTypes = AllowedBoth)

  createStrategy(tradeComissionFactor = "0.0003",
                 oneTradeRiskFactor = "0.03",
                 maxTradesKeptOpened = 3,
                 firstStopDistance = "0.0032",
                 dontOpenTradeIfStopLessThan = "0.0013",
                 stopDistanceUpperLimit = "0.01",
                 orderDelayDistance = "0.0035",
                 periodsCountToDetectSerie = 1,
                 period = Interval.minutes(90),
                 periodStartShift = Interval.minutes(30),
                 directionDetectionTolerance = "0.0004",
                 tpFactor = "0.2",
                 allowedTradeTypes = AllowedBoth)

  timer.loop

  trading dumpResultTo fileLogger

  fileLogger.close()
}
