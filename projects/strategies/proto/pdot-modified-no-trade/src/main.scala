
import java.io.File

import tas.input.Sequence

import tas.input.format.ticks.metatrader.MetatraderExportedTicks
import tas.input.format.ticks.bincache.TicksBinaryCache

import tas.output.logger.{
  PrefixTimerTime,
  Logger
}

import tas.prediction.Prediction

import tas.probing.{
  ProbeApp,
  RunValue
}

import tas.probing.running.ProbeRunner

import tas.probing.running.run.ProbeRun

import tas.probing.types.{
  Type,
  IntType,
  FileType,
  FractionType,
  IntervalType
}

import tas.readers.TicksFileMetrics

import tas.sources.periods.Ticks2Periods

import tas.sources.ticks.{
  TickSource,
  TicksFromSequence
}

import tas.strategies.activeness.{
  DefaultActiveness,
  ActivenessCondition
}

import tas.strategies.pdot.Pdot

import tas.timers.JustNowFakeTimer


import tas.types.{
  Interval,
  Fraction,
  Time
}

object Config {

  // def dumpStatistics(trading:TradingSimulation,
  //                    logger:Logger, activeness:ActivenessCondition):(Any=>Unit) = _ => {
  //     if (activeness.isActive) {
  //       trading.dumpStatus("period ended with", logger)
  //     } else {
  //       trading.dumpStatus("period of inactivity ended with", logger)
  //     }
  //   }
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

  val TicksFile                              = parameter(FileType,              "ticks",                                  "Source file with ticks to run test on")

  val Period                                 = parameter(IntervalType,          "period",                                 "Period size to work with")
  val PeriodStartShift                       = parameter(IntervalType,          "periodStartShift",                       "Period's shift from normal period start point")

  val DeviationToDetectDirection             = parameter(FractionType,          "deviationToDetectDirection",             "Price offset from period start to detect period direction")

  val Value                                  = parameter(FractionType,          "value",                                  "Value to open trade with")
}

class RunConfig(val ticksFile:File,
                val period:Interval,
                val periodStartShift:Interval,
                val deviationToDetectDirection:Fraction// ,
                // val value:Fraction
) extends Serializable


class Run(logging:ProbeRunner.Logging,
          config:RunConfig) extends ProbeRun {

  val priceSpread = Fraction.ZERO

  val timer = new JustNowFakeTimer

  val timedLogger = new PrefixTimerTime(timer,
                                        logging.runLogger)


  val inputMetrics = TicksFileMetrics.fromFile(config.ticksFile)

  val ticksSource = new TicksFromSequence(timer,
                                          Sequence.fromFile(config.ticksFile,
                                                            MetatraderExportedTicks,
                                                            TicksBinaryCache),
                                          priceSpread)
  
  val composer = new Ticks2Periods(timer,
                                   ticksSource.tickEvent,
                                   config.period,
                                   inputMetrics
                                     .firstTickTime
                                     .nextPeriodStartTime(config.period,
                                                          config.periodStartShift))

  val activenessCondition = new DefaultActiveness(timer,
                                                  inputMetrics.firstTickTime)

  // composer.periodCompleted += Config.dumpStatistics(trading, timedLogger, activenessCondition)

  val prediction = new Prediction(timer, timedLogger, ticksSource)

  new Pdot(timer,
           new Pdot.Config(activenessCondition,
                           // config.stopDistance,
                           config.deviationToDetectDirection// ,
                           // config.value
           ),
           new Pdot.Context {
             // def tradeBackend = trading
             def prediction = Run.this.prediction
             def tickSource = Run.this.ticksSource
             def periodsSource = composer
             def logger = timedLogger
           } )

  final def run():Boolean = {

    timer.callAt(inputMetrics.lastTickTime,
                 timer.stop)

    timer.loop

    prediction.dumpResultTo(logging.combinedStatsLogger)

    true
  }


}

object Probes extends ProbeApp[RunConfig]("PdotRunnersStarter") {

  def withParameter[T](parameter:Parameters.ParameterDefinition[T]):RunValue[T] = withParameter(parameter.theType,
                                                                                                parameter.name,
                                                                                                parameter.description)

  // val _leverage                               = withParameter(Parameters.Leverage)
  // val _tradeComissionFactor                   = withParameter(Parameters.TradeComissionFactor)
  // val _spread                                 = withParameter(Parameters.Spread)
  val _ticksFile                              = withParameter(Parameters.TicksFile)
  // val _startBalance                           = withParameter(Parameters.StartBalance)
  // val _probeTerminateIfEquityRelativeDrawDown = withParameter(Parameters.ProbeTerminateIfEquityRelativeDrawDown)
  // val _stopDistance                           = withParameter(Parameters.StopDistance)
  val _period                                 = withParameter(Parameters.Period)
  val _periodStartShift                       = withParameter(Parameters.PeriodStartShift)
  val _deviationToDetectDirection             = withParameter(Parameters.DeviationToDetectDirection)
  // val _value                                  = withParameter(Parameters.Value)

  def strategyName = "enter in direction detected by deviation of period opening"

  def createConfig = new RunConfig(_ticksFile,
                                   // _leverage,
                                   // _tradeComissionFactor,
                                   // _spread,
                                   // _startBalance,
                                   // _probeTerminateIfEquityRelativeDrawDown,
                                   // _stopDistance,
                                   _period,
                                   _periodStartShift,
                                   _deviationToDetectDirection// ,
                                   // _value
    )
}


object PdotRunnersStarter extends tas.probing.running.RunnersStarter("PdotProbeRunner")

object PdotProbeRunner extends ProbeRunner[RunConfig] (new Run(_, _))
