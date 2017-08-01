
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
  Boundary,
  Price
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

import tas.prediction.InZoneWhileExpressionTrue

import tas.prediction.search.value.{
  SlidingMax,
  LeftEqualRight,
  LeftGreaterThanRight,
  PeriodField,
  Value,
  Constant,
  Multiply,
  NthValueFromPast,
  MinValue,
  MaxValue,
  NormalizedByMax,
  BoolOr,
  BoolNot,
  BoolAnd
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
import tas.sources.ticks.GetterDownloader

import tas.types.Time.Moscow
import tas.types.{ Saturday, Sunday, Monday, Friday }

import tas.Bound
import tas.NotBound

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

  val StopDistance                           = parameter(FractionType,          "stopDistance",                           "Distance of stop")

  val Period                                 = parameter(IntervalType,          "period",                                 "Period size to work with")
  val PeriodStartShift                       = parameter(IntervalType,          "periodStartShift",                       "Period's shift from normal period start point")

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
                val stopDistance:Fraction,
                val period:Interval,
                val periodStartShift:Interval,
                val tradeValueGranularity:Int) extends ProbeRunSimulation.Config with Serializable



class Run(logging:ProbeRunner.Logging, config:RunConfig) extends ProbeRunSimulation(logging, config) {

  override def ticksFile = new File(config.input.fileName)


  
  val composer = new Ticks2Periods(timer,
                                   ticksSource.tickEvent,
                                   config.period,
                                   inputMetrics.firstTickTime.nextPeriodStartTime(config.period,
                                                                                  config.periodStartShift))

  val activenessCondition = new DefaultActiveness(timer,
                                                  inputMetrics.firstTickTime)

  composer.periodCompleted += Config.dumpStatistics(trading, timedLogger, activenessCondition)


  val range = new PeriodField(composer,
                  "period range",
                  _.range(Price.Bid))

  val change = new PeriodField(composer,
                               "period change",
                               _.change(Price.Bid))

  val factor = new Constant(Fraction("0.85"))

  var currentPrice:Price = null
  ticksSource.tickEvent += ((p) => { currentPrice = p})

  List(
    // // (((period range) * (0.85) > max(period change))) || ((max(period change) > period range))
    // (Buy, new BoolOr(new LeftGreaterThanRight(new Multiply(factor,
    //                                                        range),
    //                                           new MaxValue(change)),
    //                  new LeftGreaterThanRight(new MaxValue(change),
    //                                          range))),
    // // (((period change) * (0.85) > previous(2, period change))) || (!((previous(2, period range) > max(period change))))
    // (Sell, new BoolOr(new LeftGreaterThanRight(new Multiply(factor, change),
    //                                            new NthValueFromPast(2, change)),
    //                   new BoolNot(new LeftGreaterThanRight(new NthValueFromPast(2, range),
    //                                                        new MaxValue(change)))))


    // (Sell, new BoolAnd(new BoolNot(new LeftGreaterThanRight(new Multiply(factor,
    //                                                                      range),
    //                                                         new MaxValue(change))),
    //                    new LeftGreaterThanRight(range, new MaxValue(change)))),
    // (Buy, new BoolAnd(new BoolNot(new LeftGreaterThanRight(new Multiply(change, factor),
    //                                                        new NthValueFromPast(2, change))),
    //                   new LeftGreaterThanRight(new NthValueFromPast(2,
    //                                                                 range),
    //                                            new MaxValue(change)))


    // !(((period change == previous(2, period change))) || ((period range == sliding(100, max, period range))))
    (Buy, new BoolNot(new BoolOr(new LeftEqualRight(change,
                                                    new NthValueFromPast(2, change)),
                                 new LeftEqualRight(range,
                                                    new SlidingMax(100, range))))),
    // (!((normToMax(period range) > normToMax(period change)))) || ((sliding(100, max, period change) > previous(2, period change)))

    (Sell, new BoolOr(new BoolNot(new LeftGreaterThanRight(new NormalizedByMax(range),
                                                           new NormalizedByMax(change))),
                      new LeftGreaterThanRight(new SlidingMax(100, change),
                                               new NthValueFromPast(2, change))))

  ).foreach(e => new InZoneWhileExpressionTrue(() => {
                                                 val t = e._1
                                                 val request = new TradeRequest(Fraction("1000"),
                                                                                t,
                                                                                None)
                                                 val executor = trading.newTradeExecutor(request)

                                                 executor.openTrade(t.stop(currentPrice,
                                                                           config.stopDistance),
                                                                    None,
                                                                    ()=>{},
                                                                    () =>{})

                                                 () => executor.closeTrade()
                                               },
                                               e._2))

  // new InZoneWhileExpressionTrue()

  // new TfpSimple(timer,
  //                new TfpSimple.Config(activenessCondition,
  //                                  config.stopDistance,
  //                                  config.tradeValueGranularity,
  //                                  config.tradeComissionFactor),
  //              new TfpSimple.Context {
  //                def tradeBackend = trading
  //                def periodsSource = composer
  //                def logger = timedLogger
  //              } )


}

object Probes extends ProbeApp[RunConfig]("TfpRunnersStarter") {

  def withParameter[T](parameter:Parameters.ParameterDefinition[T]):RunValue[T] = withParameter(parameter.theType,
                                                                                                parameter.name,
                                                                                                parameter.description)

  val _leverage                               = withParameter(Parameters.Leverage)
  val _tradeComissionFactor                   = withParameter(Parameters.TradeComissionFactor)
  val _sourceFile                             = withParameter(Parameters.SourceFile)
  val _startBalance                           = withParameter(Parameters.StartBalance)
  val _probeTerminateIfEquityRelativeDrawDown = withParameter(Parameters.ProbeTerminateIfEquityRelativeDrawDown)
  val _stopDistance                           = withParameter(Parameters.StopDistance)
  val _period                                 = withParameter(Parameters.Period)
  val _periodStartShift                       = withParameter(Parameters.PeriodStartShift)
  val _tradeValueGranularity                  = withParameter(Parameters.TradeValueGranularity)

  def strategyName = "simplified previous day direction enter"

  def createConfig = new RunConfig(new Input(_sourceFile.value.toString),
                                   _leverage,
                                   _tradeComissionFactor,
                                   _startBalance,
                                   _probeTerminateIfEquityRelativeDrawDown,
                                   _stopDistance,
                                   _period,
                                   _periodStartShift,
                                   _tradeValueGranularity)
}

object TfpRunnersStarter extends tas.probing.running.RunnersStarter("TfpProbeRunner")

object TfpProbeRunner extends ProbeRunner[RunConfig] (new Run(_, _))




