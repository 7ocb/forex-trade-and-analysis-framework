
import scala.collection.mutable.HashMap
import tas.ActiveValue
import tas.ActiveExpresion
import tas.ParameterSet
import tas.types.{
  Interval,
  Period,
  Fraction,
  Time,
  Buy,
  Sell,
  Boundary
}

import tas.probing.types.{
  Type,
  IntType,
  FileType,
  FractionType,
  IntervalType,
  StringType
  
}

import tas.readers.TicksFileMetrics
import tas.readers.PeriodsSequence
import tas.input.Sequence
import tas.input.format.ticks.metatrader.MetatraderExportedTicks
import tas.input.format.ticks.bincache.TicksBinaryCache
import tas.readers.ShiftedPeriodsSequence
import tas.sources.ticks.PeriodsToTicksSource
import tas.sources.ticks.TickSource
import tas.sources.ticks.MetatraderExportedTicksSource
import tas.sources.StatisticComputation
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

import tas.trading.simulation.SimulationError


import tas.trading.TradeHandle
import tas.trading.TradeExecutor
import tas.trading.simulation.TradingSimulation
import tas.trading.TradeRequest


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

import tas.ppdesimple.strategy.{
  PpdeSimple,
  AllowedTrades,
  IfNoDirection
}

import tas.strategies.activeness.{
  ActivenessCondition,
  DefaultActiveness
}

object Config {
  val tradeValueGranularity = 1000



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

  val Leverage                               = parameter(IntType,               "leverage",                               "Leverage for trades")
  val TradeComissionFactor                   = parameter(FractionType,          "tradeComissionFactor",                   "Trade comission factor - offset of price on close against trade")
  val SourceFile                             = parameter(FileType,              "ticks",                                  "Source file with ticks to run test on")
  val StartBalance                           = parameter(FractionType,          "startBalance",                           "Start balance")
  val ProbeTerminateIfEquityRelativeDrawDown = parameter(FractionType,          "probeTerminateIfEquityRelativeDrawDown", "Equity relative drawdown to prematurely fail probe (0 to disable).")

  val TradeRiskFactor                        = parameter(FractionType,          "tradeRiskFactor",                        "Fraction of balance to risk in one trade")
  val StopDistance                           = parameter(FractionType,          "stopDistance",                           "Distance of stop")
  val TakeDistance                           = parameter(FractionType,          "takeDistance",                           "Distance of take profit")
  val OrderDelayDistance                     = parameter(FractionType,          "orderDelayDistance",                     "Distance to delay opening a trade")
  val PeriodsCountToDetectSerie              = parameter(IntType,               "periodsCountToDetectSerie",              "Count of consecutive same-direction periods to count as serie")

  val Period                                 = parameter(IntervalType,          "period",                                 "Period size to work with")
  val PeriodStartShift                       = parameter(IntervalType,          "periodStartShift",                       "Period's shift from normal period start point")

  val DirectionDetectionTolerance            = parameter(FractionType,          "directionDetectionTolerance",            "Tolerance when detecting period direction")

  val IfNoDirection                          = parameter(IfNoDirectionType,     "ifNoDirection",                          "How to treat period with no direction,as same or opposite as previous")

  val AllowedTrades                          = parameter(AllowedTradesType,     "tradesDirectionAllowed",                 "Trade types, which allowed for strategy")
  val TradeValueGranularity                  = parameter(IntType,               "tradeValueGranularity",                  "Granularity of trade value, as allowed by broker")


  val HubAddress  = parameter(StringType, "hubAddress", "address of the hub")
  val HubPort     = parameter(IntType, "hubPort", "hub port")
  val HubTicksKey = parameter(StringType, "hubTicksKey", "key to request ticks source from hub")
}

case class Input(val fileName:String) extends Serializable

class RunConfig(val input:Input,
                val leverage:Int,
                val tradeComissionFactor:Fraction,
                val startBalance:Fraction,
                val probeTerminateIfEquityRelativeDrawDown:Fraction,
                val tradeRiskFactor:Fraction,
                val stopDistance:Fraction,
                val takeDistance:Fraction,
                val orderDelayDistance:Fraction,
                val periodsCountToDetectSerie:Int,
                val period:Interval,
                val periodStartShift:Interval,
                val directionDetectionTolerance:Fraction,
                val ifNoDirection:IfNoDirection,
                val allowedTrades:AllowedTrades,
                val tradeValueGranularity:Int) extends ProbeRunSimulation.Config with Serializable



class Run(logging:ProbeRunner.Logging, config:RunConfig) extends ProbeRunSimulation(logging, config) {

  override def ticksFile = new File(config.input.fileName)


  
  val composer = new Ticks2Periods(timer,
                                   ticksSource.tickEvent,
                                   config.period,
                                   PpdeSimple.nextPeriodStartTime(inputMetrics.firstTickTime,
                                                                config.period,
                                                                config.periodStartShift))

  val activenessCondition = new DefaultActiveness(timer,
                                                  inputMetrics.firstTickTime)

  composer.periodCompleted += Config.dumpStatistics(trading, timedLogger, activenessCondition)

  new PpdeSimple(timer,
                 new PpdeSimple.Config(activenessCondition,
                                   config.stopDistance,
                                   config.takeDistance,
                                   config.orderDelayDistance,
                                   config.tradeValueGranularity,
                                   config.ifNoDirection,
                                   config.directionDetectionTolerance,
                                   config.periodsCountToDetectSerie,
                                   config.tradeRiskFactor,
                                   config.tradeComissionFactor,
                                   config.allowedTrades),
               new PpdeSimple.Context {
                 def tradeBackend = trading
                 def periodsSource = composer
                 def logger = timedLogger
               } )


}

object Probes extends ProbeApp[RunConfig]("PpdeSimpleRunnersStarter") {

  def withParameter[T](parameter:Parameters.ParameterDefinition[T]):RunValue[T] = withParameter(parameter.theType,
                                                                                                parameter.name,
                                                                                                parameter.description)

  val _leverage                               = withParameter(Parameters.Leverage)
  val _tradeComissionFactor                   = withParameter(Parameters.TradeComissionFactor)
  val _sourceFile                             = withParameter(Parameters.SourceFile)
  val _startBalance                           = withParameter(Parameters.StartBalance)
  val _probeTerminateIfEquityRelativeDrawDown = withParameter(Parameters.ProbeTerminateIfEquityRelativeDrawDown)
  val _tradeRiskFactor                        = withParameter(Parameters.TradeRiskFactor)
  val _stopDistance                           = withParameter(Parameters.StopDistance)
  val _takeDistance                           = withParameter(Parameters.TakeDistance)
  val _orderDelayDistance                     = withParameter(Parameters.OrderDelayDistance)
  val _periodsCountToDetectSerie              = withParameter(Parameters.PeriodsCountToDetectSerie)
  val _period                                 = withParameter(Parameters.Period)
  val _periodStartShift                       = withParameter(Parameters.PeriodStartShift)
  val _directionDetectionTolerance            = withParameter(Parameters.DirectionDetectionTolerance)
  val _ifNoDirection                          = withParameter(Parameters.IfNoDirection)
  val _allowedTrades                          = withParameter(Parameters.AllowedTrades)
  val _tradeValueGranularity                  = withParameter(Parameters.TradeValueGranularity)

  

  def strategyName = "simplified previous day direction enter"

  def createConfig = new RunConfig(new Input(_sourceFile.value.toString),
                                   _leverage,
                                   _tradeComissionFactor,
                                   _startBalance,
                                   _probeTerminateIfEquityRelativeDrawDown,
                                   _tradeRiskFactor,
                                   _stopDistance,
                                   _takeDistance,
                                   _orderDelayDistance,
                                   _periodsCountToDetectSerie,
                                   _period,
                                   _periodStartShift,
                                   _directionDetectionTolerance,
                                   _ifNoDirection,
                                   _allowedTrades,
                                   _tradeValueGranularity)
}

object PpdeSimpleRunnersStarter extends tas.probing.running.RunnersStarter("PpdeSimpleProbeRunner")

object PpdeSimpleProbeRunner extends ProbeRunner[RunConfig] (new Run(_, _))




