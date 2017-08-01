
import scala.collection.mutable.HashMap
import tas.ActiveValue
import tas.ActiveExpresion
import tas.ParameterSet
import tas.types.{Interval, Period, Fraction, Time}

import tas.probing.types.{
  Type,
  IntType,
  FileType,
  FractionType,
  IntervalType,
  StringType
}

// import tas.readers.TicksFileMetrics
import tas.readers.PeriodsSequence
import tas.input.Sequence
// import tas.input.format.ticks.metatrader.MetatraderExportedTicks
// import tas.input.format.ticks.bincache.TicksBinaryCache
import tas.readers.ShiftedPeriodsSequence
import tas.sources.ticks.PeriodsToTicksSource
import tas.sources.ticks.TickSource
// import tas.sources.ticks.MetatraderExportedTicksSource
// import tas.sources.StatisticComputation
import tas.sources.periods.Ticks2Periods

import tas.hub.clients.HubConnectionTicksSource
import tas.service.AddressByName

import tas.utils.args.{
  ArgumentsSource,
  PropertyFileArguments,
  CommandLineArguments
}

import tas.events.{
  Subscription,
  SyncSubscription,
  Event
}
import tas.timers.Timer
import tas.timers.JustNowFakeTimer

import java.io.File

import tas.trading.CloseCondition
import tas.trading.KeepTradeOpened
import tas.trading.CloseTrade



import tas.trading.Boundary
import tas.trading.TradeHandle
import tas.trading.TradeExecutor
import tas.trading.simulation.TradingSimulation
import tas.trading.TradeRequest
import tas.trading.Buy
import tas.trading.Sell

import tas.output.logger.{ScreenLogger, PrefixTimerTime, Logger, FileLogger}

import tas.probing.ProbeApp
import tas.probing.RunValue
import tas.probing.running.ProbeRunner
import tas.probing.running.run.ProbeRunSimulation


import tas.concurrency.RunLoop

import tas.timers.RealTimeTimer

import tas.sources.ticks.decomposer.PeriodToOpenMinMaxClose
import tas.sources.ticks.TicksFromRemotePeriods
import tas.sources.ticks.TicksFromSequence
import tas.sources.finam.FinamUrlFactory
import tas.sources.ticks.GetterDownloader

import tas.types.Time.Moscow
import tas.types.{ Saturday, Sunday, Monday, Friday }

import tas.Bound
import tas.NotBound

import tas.strategies.activeness.{
  ActivenessCondition,
  DefaultActiveness
}

import tas.previousdaydirection.strategy.{
  Strategy,
  AllowedTradeTypes
}

object Config {
  val tradeValueGranularity = 1000

  def activeness(timer:Timer, start:Time) = new DefaultActiveness(timer, start)


  def dumpStatistics(trading:TradingSimulation, logger:Logger, activeness:ActivenessCondition):(Any=>Unit) = _ => {
      if (activeness.isActive) {
        trading.dumpStatus("period ended with", logger)
      } else {
        trading.dumpStatus("period of inactivity ended with", logger)
      }
    }
}

object Parameters {
  case class ParameterDefinition[T](val theType:Type[T],
                                    val name:String,
                                    val description:String)

  def parameter[T](theType:Type[T],
                   name:String,
                   description:String):ParameterDefinition[T] = new ParameterDefinition(theType,
                                                                                        name,
                                                                                        description)

  val SourceFile                             = parameter(FileType,              "ticks",                                  "Source file with ticks to run test on")
  val StartBalance                           = parameter(FractionType,          "startBalance",                           "Start balance")
  val TradeComissionFactor                   = parameter(FractionType,          "tradeComissionFactor",                   "Trade comission factor - offset of price on close against trade")
  val Leverage                               = parameter(IntType,               "leverage",                               "Leverage for trades")
  val OneTradeRiskFactor                     = parameter(FractionType,          "oneTradeRiskFactor",                     "Fraction of balance to risk in one trade")
  val MaxTradesKeptOpened                    = parameter(IntType,               "maxTradesKeptOpened",                    "Maximal trades in serie")
  val MaxSerieRiskFactor                     = parameter(FractionType,          "maxSerieRiskFactor",                     "Fraction of balance to risk in whole serie")
  val FirstStopDistance                      = parameter(FractionType,          "firstStopDistance",                      "Distance of first stop")
  val DontOpenTradeIfStopLessThan            = parameter(FractionType,          "dontOpenTradeIfStopLessThan",            "Value of minimal stop distance to open trade")
  val StopDistanceUpperLimit                 = parameter(FractionType,          "stopDistanceUpperLimit",                 "Upper limit of stop distance")
  val OrderDelayDistance                     = parameter(FractionType,          "orderDelayDistance",                     "Distance to delay opening a trade")
  val PeriodsCountToDetectSerie              = parameter(IntType,               "periodsCountToDetectSerie",              "Count of consecutive same-direction periods to count as serie")
  val Period                                 = parameter(IntervalType,          "period",                                 "Period size to work with")
  val PeriodStartShift                       = parameter(IntervalType,          "periodStartShift",                       "Period's shift from normal period start point")
  val DirectionDetectionTolerance            = parameter(FractionType,          "directionDetectionTolerance",            "Tolerance when detecting period direction")
  val TpFactor                               = parameter(FractionType,          "tpFactor",                               "Times take profit distance differ from stop distance (tpDist = stopDist * tpFactor)")
  val ProbeTerminateIfEquityRelativeDrawDown = parameter(FractionType,          "probeTerminateIfEquityRelativeDrawDown", "Equity relative drawdown to prematurely fail probe (0 to disable).")
  val AllowedTradeTypes                      = parameter(AllowedTradeTypesType, "tradesDirectionAllowed",                "Trade types, which allowed for strategy")


  val HubAddress  = parameter(StringType, "hubAddress", "address of the hub")
  val HubPort     = parameter(IntType, "hubPort", "hub port")
  val HubTicksKey = parameter(StringType, "hubTicksKey", "key to request ticks source from hub")
}

case class Input(val fileName:String) extends Serializable



class RunConfig(val input:Input,
                val startBalance:Fraction,
                val tradeComissionFactor:Fraction,
                val leverage:Int,
                val oneTradeRiskFactor:Fraction,
                val maxTradesKeptOpened:Int,
                val firstStopDistance:Fraction,
                val dontOpenTradeIfStopLessThan:Fraction,
                val stopDistanceUpperLimit:Fraction,
                val orderDelayDistance:Fraction,
                val periodsCountToDetectSerie:Int,
                val period:Interval,
                val periodStartShift:Interval,
                val directionDetectionTolerance:Fraction,
                val tpFactor:Fraction,
                val probeTerminateIfEquityRelativeDrawDown:Fraction,
                val allowedTradeTypes:AllowedTradeTypes) extends ProbeRunSimulation.Config with Serializable


class Run(logging:ProbeRunner.Logging, config:RunConfig) extends ProbeRunSimulation(logging, config) {

  override def ticksFile = new File(config.input.fileName)

  val composer = new Ticks2Periods(timer,
                                   config.period,
                                   Strategy.nextPeriodStartTime(inputMetrics.firstTickTime,
                                                                config.period,
                                                                config.periodStartShift))

  composer.bindTo(ticksSource.tickEvent)

  val activenessCondition = Config.activeness(timer, inputMetrics.firstTickTime)

  composer.periodCompleted += Config.dumpStatistics(trading, timedLogger, activenessCondition)

  new Strategy(timer,
               new Strategy.Config(activenessCondition,
                                   config.orderDelayDistance,
                                   config.tpFactor,
                                   new Strategy.StopsSettings(config.firstStopDistance,
                                                              config.dontOpenTradeIfStopLessThan,
                                                              config.stopDistanceUpperLimit),
                                   config.periodsCountToDetectSerie,
                                   config.oneTradeRiskFactor,
                                   config.maxTradesKeptOpened,
                                   config.tradeComissionFactor,
                                   Config.tradeValueGranularity,
                                   config.directionDetectionTolerance,
                                   config.allowedTradeTypes),
               new Strategy.Context {
                 def tradeBackend = trading
                 def periodsSource = composer
                 def logger = timedLogger
               } )

}

abstract class RealtimeRunBase extends App {
  def createTicksSource(logger:Logger,
                        timer:Timer,
                        runLoop:RunLoop):TickSource

  def requiredParameters:List[Parameters.ParameterDefinition[_]] =
    List(Parameters.StartBalance,
         Parameters.TradeComissionFactor,
         Parameters.Leverage,
         Parameters.OneTradeRiskFactor,
         Parameters.MaxTradesKeptOpened,
         Parameters.FirstStopDistance,
         Parameters.DontOpenTradeIfStopLessThan,
         Parameters.StopDistanceUpperLimit,
         Parameters.OrderDelayDistance,
         Parameters.PeriodsCountToDetectSerie,
         Parameters.Period,
         Parameters.DirectionDetectionTolerance,
         Parameters.TpFactor)

  val RunPropertiesFile = "run.properties"

  val commandLine = new CommandLineArguments(args)

  if (commandLine.value("help").isDefined) {

    println("following parameters are needed to be provided in " + RunPropertiesFile + ":")

    requiredParameters.foreach(param => println(param.name + " - " + param.description))

    sys.exit(0)
  }


  val runLoop = new RunLoop
  val timer = new RealTimeTimer(runLoop)

  val timedLogger =
    new PrefixTimerTime(timer,
                        new FileLogger("realtime-run.log",
                                       FileLogger.PlainReopening))

  val ticksSourceLogger =
    new PrefixTimerTime(timer,
                        new FileLogger("ticks-source.log",
                                       FileLogger.PlainReopening))

  val ticksLogger =
    new PrefixTimerTime(timer,
                        new FileLogger("processed-ticks.log",
                                       FileLogger.PlainReopening))



  val runProperties = new PropertyFileArguments(RunPropertiesFile)

  def parameter[T](param:Parameters.ParameterDefinition[T]):T = {

    def errorNoParameter() = {
      println("value: " + param.name + " is not specified")
      sys.exit(1)
    }

    val value = runProperties.value(param.name)

    if (value.isEmpty) errorNoParameter()

    val parsed = param.theType.parse(value.get,
                                     timedLogger)

    if (parsed.isEmpty) errorNoParameter()

    val result = parsed.head

    timedLogger.log(param.name + ": " + result)

    result
  }

  timedLogger.log("================================================================================")
  timedLogger.log("Starting simulation")
  timedLogger.log("==== Parameters: ")

  val leverage = parameter(Parameters.Leverage)
  val orderDelayDistance = parameter(Parameters.OrderDelayDistance)
  val tpFactor = parameter(Parameters.TpFactor)
  val firstStopDistance = parameter(Parameters.FirstStopDistance)
  val dontOpenTradeIfStopLessThan = parameter(Parameters.DontOpenTradeIfStopLessThan)
  val stopDistanceUpperLimit = parameter(Parameters.StopDistanceUpperLimit)
  val periodsCountToDetectSerie = parameter(Parameters.PeriodsCountToDetectSerie)
  val oneTradeRiskFactor = parameter(Parameters.OneTradeRiskFactor)
  val maxTradesKeptOpened = parameter(Parameters.MaxTradesKeptOpened)
  val tradeComissionFactor = parameter(Parameters.TradeComissionFactor)
  val directionDetectionTolerance = parameter(Parameters.DirectionDetectionTolerance)
  val allowedTradeTypes = parameter(Parameters.AllowedTradeTypes)

  val startBalance = parameter(Parameters.StartBalance)
  val period = parameter(Parameters.Period)
  val periodStartShift = parameter(Parameters.PeriodStartShift)

  val ticksSource = createTicksSource(ticksSourceLogger,
                                      timer,
                                      runLoop)

  val trading = new TradingSimulation(timer,
                                      leverage,
                                      startBalance,
                                      timedLogger,
                                      tradeComissionFactor,
                                      ticksSource.tickEvent)

  val startTime = Strategy.nextPeriodStartTime(timer.currentTime,
                                               period,
                                               periodStartShift)

  timedLogger.log("period counting start time: ", startTime)
  timedLogger.log("--------------------------------------------------------------------------------")
  timedLogger.log("ticks source: " + ticksSource)
  timedLogger.log("================================================================================")

  ticksSource.tickEvent += (tick => ticksLogger.log("tick: " + tick))

  val composer = new Ticks2Periods(timer,
                                   period,
                                   startTime)

  composer.bindTo(ticksSource.tickEvent)

  val activenessCondition = Config.activeness(timer, timer.currentTime)

  composer.periodCompleted += Config.dumpStatistics(trading, timedLogger, activenessCondition)
  composer.emptyPeriodEnded += Config.dumpStatistics(trading, timedLogger, activenessCondition)
  
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
               } )

  runLoop()

}

object RealtimeRunFinam extends RealtimeRunBase {

  def createTicksSource(logger:Logger,
                        timer:Timer,
                        runLoop:RunLoop):TickSource = {
    new TicksFromRemotePeriods(timer,
                               new GetterDownloader(runLoop,
                                                    new FinamUrlFactory(timer),
                                                    logger),
                               Interval.seconds(10))
  }
}

object RealtimeRunMetatrader extends RealtimeRunBase {

  override def requiredParameters = (super.requiredParameters
                                       ++ List(Parameters.HubAddress,
                                               Parameters.HubPort,
                                               Parameters.HubTicksKey))

  def createTicksSource(logger:Logger,
                        timer:Timer,
                        runLoop:RunLoop):TickSource = {

    val hubAddress = parameter(Parameters.HubAddress)
    val hubPort = parameter(Parameters.HubPort)
    val hubTicksKey = parameter(Parameters.HubTicksKey)


    new HubConnectionTicksSource(runLoop,
                                 logger,
                                 new AddressByName(hubAddress, hubPort),
                                 hubTicksKey)
    // new MetatraderExportedTicksSource(runLoop,
    //                                   timer,
    //                                   logger,
    //                                   Interval.seconds(5),
    //                                   new File("/home/user/.wine/drive_c/Program Files/MetaTrader WhoTrades/experts/files/AUDUSD"))
  }
}

object Probes extends ProbeApp[RunConfig]("PddeRunnersStarter") {

  def withParameter[T](parameter:Parameters.ParameterDefinition[T]):RunValue[T] = withParameter(parameter.theType,
                                                                                                parameter.name,
                                                                                                parameter.description)

  val _sourceFile                             = withParameter(Parameters.SourceFile)
  val _startBalance                           = withParameter(Parameters.StartBalance)
  val _tradeComissionFactor                   = withParameter(Parameters.TradeComissionFactor)
  val _leverage                               = withParameter(Parameters.Leverage)
  val _oneTradeRiskFactor                     = withParameter(Parameters.OneTradeRiskFactor)
  val _maxTradesKeptOpened                    = withParameter(Parameters.MaxTradesKeptOpened)
  val _maxSerieRiskFactor                     = withParameter(Parameters.MaxSerieRiskFactor)
  val _firstStopDistance                      = withParameter(Parameters.FirstStopDistance)
  val _dontOpenTradeIfStopLessThan            = withParameter(Parameters.DontOpenTradeIfStopLessThan)
  val _stopDistanceUpperLimit                 = withParameter(Parameters.StopDistanceUpperLimit)
  val _orderDelayDistance                     = withParameter(Parameters.OrderDelayDistance)
  val _periodsCountToDetectSerie              = withParameter(Parameters.PeriodsCountToDetectSerie)
  val _period                                 = withParameter(Parameters.Period)
  val _periodStartShift                       = withParameter(Parameters.PeriodStartShift)
  val _directionDetectionTolerance            = withParameter(Parameters.DirectionDetectionTolerance)
  val _tpFactor                               = withParameter(Parameters.TpFactor)
  val _probeTerminateIfEquityRelativeDrawDown = withParameter(Parameters.ProbeTerminateIfEquityRelativeDrawDown)
  val _allowedTradeTypes                      = withParameter(Parameters.AllowedTradeTypes)
  

  def strategyName = "previous day direction enter"

  override def isRunValid:Boolean = ((_oneTradeRiskFactor.value * _maxTradesKeptOpened.value) <= _maxSerieRiskFactor.value
                                       && _startBalance.value > 0
                                       && _periodsCountToDetectSerie.value > 0
                                       && _period.isCanFillDay)

  def createConfig = new RunConfig(new Input(_sourceFile.value.toString),
                                   _startBalance.value,
                                   _tradeComissionFactor.value,
                                   _leverage.value,
                                   _oneTradeRiskFactor.value,
                                   _maxTradesKeptOpened.value,
                                   _firstStopDistance.value,
                                   _dontOpenTradeIfStopLessThan.value,
                                   _stopDistanceUpperLimit.value,
                                   _orderDelayDistance.value,
                                   _periodsCountToDetectSerie.value,
                                   _period.value,
                                   _periodStartShift.value,
                                   _directionDetectionTolerance.value,
                                   _tpFactor.value,
                                   _probeTerminateIfEquityRelativeDrawDown.value,
                                   _allowedTradeTypes.value)
}

object PddeRunnersStarter extends tas.probing.running.RunnersStarter("PddeProbeRunner")

object PddeProbeRunner extends ProbeRunner[RunConfig](new Run(_, _))



