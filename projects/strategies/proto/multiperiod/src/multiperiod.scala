
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

import tas.strategies.activeness.{
  ActivenessCondition,
  DefaultActiveness
}


import tas.Bound
import tas.NotBound

import tas.multiperiod.strategy.{
  Strategy,
  EnterDirection
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

  val SourceFile                             = parameter(FileType,               "ticks",                                  "Source file with ticks to run test on")

  val StartBalance                           = parameter(FractionType,           "startBalance",                           "Start balance")
  val TradeComissionFactor                   = parameter(FractionType,           "tradeComissionFactor",                   "Trade comission factor - offset of price on close against trade")
  val Leverage                               = parameter(IntType,                "leverage",                               "Leverage for trades")

  val OneTradeRiskFactor                     = parameter(FractionType,           "oneTradeRiskFactor",                     "Fraction of balance to risk in one trade")
  val StopDistance                           = parameter(FractionType,           "stopDistance",                           "The distance of stop set for the trade")
  val EnterDirection                         = parameter(EnterDirectionType,     "enterDirection",                         "Direction to enter according to signal, direct: Up->Buy, opposite: Up->Sell")

  val Intervals                              = parameter(ListType(IntervalType), "intervals",                              "Intervals to work with")
  val DirectionDetectionTolerance            = parameter(FractionType,           "directionDetectionTolerance",            "The difference between open and close to detect direction")

  val ProbeTerminateIfEquityRelativeDrawDown = parameter(FractionType,           "probeTerminateIfEquityRelativeDrawDown", "Equity relative drawdown to prematurely fail probe (0 to disable).")

}

case class Input(val fileName:String) extends Serializable



class RunConfig(val input:Input,
                val startBalance:Fraction,
                val tradeComissionFactor:Fraction,
                val leverage:Int,
                val oneTradeRiskFactor:Fraction,
                val stopDistance:Fraction,
                val enterDirection:EnterDirection,
                val intervals:List[Interval],
                val directionDetectionTolerance:Fraction,
                val probeTerminateIfEquityRelativeDrawDown:Fraction) extends ProbeRunSimulation.Config with Serializable


class Run(logging:ProbeRunner.Logging, config:RunConfig) extends ProbeRunSimulation(logging, config) {

  override def ticksFile = new File(config.input.fileName)

  val activenessCondition = new DefaultActiveness(timer,
                                                  inputMetrics.firstTickTime)


  new Strategy(timer,
               new Strategy.Config(activenessCondition,
                                   config.oneTradeRiskFactor,
                                   config.stopDistance,
                                   config.enterDirection,
                                   config.tradeComissionFactor,
                                   Config.tradeValueGranularity,
                                   config.directionDetectionTolerance,
                                   config.intervals),
               new Strategy.Context {
                 def tradeBackend = trading
                 def tickSource = Run.this.ticksSource
                 def logger = timedLogger
                 def balance = trading.balance
               } )

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
  val _stopDistance                           = withParameter(Parameters.StopDistance)
  val _enterDirection                         = withParameter(Parameters.EnterDirection)
  val _intervals                              = withParameter(Parameters.Intervals)

  val _directionDetectionTolerance            = withParameter(Parameters.DirectionDetectionTolerance)

  val _probeTerminateIfEquityRelativeDrawDown = withParameter(Parameters.ProbeTerminateIfEquityRelativeDrawDown)

  def strategyName = "multiperiod check"

  def createConfig = new RunConfig(new Input(_sourceFile.value.toString),
                                   _startBalance.value,
                                   _tradeComissionFactor.value,
                                   _leverage.value,
                                   _oneTradeRiskFactor.value,
                                   _stopDistance.value,
                                   _enterDirection.value,
                                   _intervals.value,
                                   _directionDetectionTolerance.value,
                                   _probeTerminateIfEquityRelativeDrawDown.value)
}

object PddeRunnersStarter extends tas.probing.running.RunnersStarter("PddeProbeRunner")

object PddeProbeRunner extends ProbeRunner[RunConfig](new Run(_, _))



