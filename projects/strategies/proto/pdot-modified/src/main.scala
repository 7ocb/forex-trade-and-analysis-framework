
import java.io.File

import tas.output.logger.Logger

import tas.probing.{
  ProbeApp,
  RunValue
}

import tas.probing.running.ProbeRunner

import tas.probing.running.run.ProbeRunSimulation

import tas.probing.types.{
  Type,
  IntType,
  FileType,
  FractionType,
  IntervalType
}

import tas.sources.periods.Ticks2Periods

import tas.strategies.activeness.{
  DefaultActiveness,
  ActivenessCondition
}

import tas.strategies.pdot.Pdot

import tas.trading.simulation.TradingSimulation

import tas.types.{
  Interval,
  Fraction,
  Time
}

object Config {

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
  val Spread                                 = parameter(FractionType,          "spread",                                 "Spread to simulate for history")
  val SourceFile                             = parameter(FileType,              "ticks",                                  "Source file with ticks to run test on")
  val StartBalance                           = parameter(FractionType,          "startBalance",                           "Start balance")
  val ProbeTerminateIfEquityRelativeDrawDown = parameter(FractionType,          "probeTerminateIfEquityRelativeDrawDown", "Equity relative drawdown to prematurely fail probe (0 to disable).")

  val StopDistance                           = parameter(FractionType,          "stopDistance",                           "Distance of stop")

  val Period                                 = parameter(IntervalType,          "period",                                 "Period size to work with")
  val PeriodStartShift                       = parameter(IntervalType,          "periodStartShift",                       "Period's shift from normal period start point")

  val ThresholdToDetectDirection             = parameter(FractionType,          "thresholdToDetectDirection",             "Price offset from period start to detect period direction")

  val Value                                  = parameter(FractionType,          "value",                                  "Value to open trade with")
}

class RunConfig(val ticksFile:File,
                val leverage:Int,
                val tradeComissionFactor:Fraction,
                val spread:Fraction,
                val startBalance:Fraction,
                val probeTerminateIfEquityRelativeDrawDown:Fraction,
                val stopDistance:Fraction,
                val period:Interval,
                val periodStartShift:Interval,
                val thresholdToDetectDirection:Fraction,
                val value:Fraction) extends ProbeRunSimulation.Config with Serializable


class Run(logging:ProbeRunner.Logging,
          config:RunConfig) extends ProbeRunSimulation(logging, config) {

  override def priceSpread = config.spread

  override def ticksFile = config.ticksFile
  
  val composer = new Ticks2Periods(timer,
                                   ticksSource.tickEvent,
                                   config.period,
                                   inputMetrics
                                     .firstTickTime
                                     .nextPeriodStartTime(config.period,
                                                          config.periodStartShift))

  val activenessCondition = new DefaultActiveness(timer,
                                                  inputMetrics.firstTickTime)

  composer.periodCompleted += Config.dumpStatistics(trading, timedLogger, activenessCondition)

  new Pdot(timer,
           new Pdot.Config(activenessCondition,
                           config.stopDistance,
                           config.thresholdToDetectDirection,
                           config.value),
           new Pdot.Context {
             def tradeBackend = trading
             def tickSource = Run.this.ticksSource
             def periodsSource = composer
             def logger = timedLogger
           } )


}

object Probes extends ProbeApp[RunConfig]("PdotRunnersStarter") {

  def withParameter[T](parameter:Parameters.ParameterDefinition[T]):RunValue[T] = withParameter(parameter.theType,
                                                                                                parameter.name,
                                                                                                parameter.description)

  val _leverage                               = withParameter(Parameters.Leverage)
  val _tradeComissionFactor                   = withParameter(Parameters.TradeComissionFactor)
  val _spread                                 = withParameter(Parameters.Spread)
  val _sourceFile                             = withParameter(Parameters.SourceFile)
  val _startBalance                           = withParameter(Parameters.StartBalance)
  val _probeTerminateIfEquityRelativeDrawDown = withParameter(Parameters.ProbeTerminateIfEquityRelativeDrawDown)
  val _stopDistance                           = withParameter(Parameters.StopDistance)
  val _period                                 = withParameter(Parameters.Period)
  val _periodStartShift                       = withParameter(Parameters.PeriodStartShift)
  val _thresholdToDetectDirection             = withParameter(Parameters.ThresholdToDetectDirection)
  val _value                                  = withParameter(Parameters.Value)

  def strategyName = "enter in direction detected by threshold of period opening"

  def createConfig = new RunConfig(_sourceFile,
                                   _leverage,
                                   _tradeComissionFactor,
                                   _spread,
                                   _startBalance,
                                   _probeTerminateIfEquityRelativeDrawDown,
                                   _stopDistance,
                                   _period,
                                   _periodStartShift,
                                   _thresholdToDetectDirection,
                                   _value)
}


object PdotRunnersStarter extends tas.probing.running.RunnersStarter("PdotProbeRunner")

object PdotProbeRunner extends ProbeRunner[RunConfig] (new Run(_, _))
