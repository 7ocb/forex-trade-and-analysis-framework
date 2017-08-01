
import java.io.File

import tas.types.Time
import tas.types.Fraction

import tas.events.{
  Subscription,
  SyncCallSubscription
}

import tas.input.Sequence
import tas.input.format.ticks.metatrader.MetatraderExportedTicks
import tas.input.format.ticks.bincache.TicksBinaryCache

import tas.prediction.zones.{
  Zone,
  ZonesSet,
  PriceZoneTracker,
  ComplementSearch
}

// import tas.periodstatisticscollector.collectors.{
//   Collector,
//   PeriodValue,
//   CalculatedValue,
//   PeriodWay,
//   SlidingAverageCollector,
//   DeltaCollector
// }


// import tas.prediction.Prediction

import prediction.search.executor.InZoneWhileExpressionTrue

import tas.prediction.search.value.Comparsions
import tas.prediction.search.equivalency.Equivalency
import tas.prediction.search.equivalency.Equivalency.{
  Slot,
  ConvertSlots
}

import tas.prediction.search.value.{
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


import tas.probing.{
  ProbeApp,
  RunValue
}

import tas.probing.running.{
  ProbeRunner,
  RunnersStarter
}

import tas.probing.running.run.ProbeRun

import tas.probing.types.{
  Type,
  IntType,
  FileType,
  IntervalType
}

import tas.types.{
  Interval,
  Fraction,
  Price,
  PeriodBid,
  Period,
  Buy,
  Sell,
  TimedTick
}


import tas.timers.{
  Timer,
  JustNowFakeTimer
}

import tas.output.logger.{
  Logger,
  LogPrefix,
  NullLogger,
  PrefixTimerTime
}


import tas.readers.TicksFileMetrics

import tas.sources.ticks.{
  TickSource,
  TicksFromSequence
}
import tas.sources.periods.{
  PeriodSource,
  Ticks2Periods
}

import tas.sources.PeriodDirection



import scala.collection.mutable.ListBuffer
import scala.collection.mutable.HashMap

case class RunConfig(sourceTicks:File,
                     period:Interval,
                     periodStartShift:Interval,
                     slidingAverageSteps:Int)

object Run {
  val Spread = Fraction("0.0002")
}



// object Mods {
//   object Min extends Modificator[Fraction] {
//     def apply(value:Value[Fraction]) = new MinValue(value)

//     def isDeniedAfter
//   }
// }

class SlotsToExpressions(values:List[Value[Boolean]]) extends Equivalency.ConvertSlots[Value[Boolean]] {
  def convertAnd(left:Slot, right:Slot):Value[Boolean] = new BoolAnd(convert(left), convert(right))
  def convertOr(left:Slot, right:Slot):Value[Boolean] = new BoolOr(convert(left), convert(right))
  def convertNot(sub:Slot):Value[Boolean] = new BoolNot(convert(sub))
  def convertValue(index:Int):Value[Boolean] = values(index)
}

class SearchForPredictors(logging:ProbeRunner.Logging,
                          config:RunConfig) extends RunBase(logging, config) {
  val booleanPermutationsDeep = 2
  val nthFromPast = 2
  val multiplier = Fraction("0.85")

  val range = new PeriodField(periodsComposer,
                              "period range",
                              _.range(priceAccessor))

  val change = new PeriodField(periodsComposer,
                               "period change",
                               _.change(priceAccessor))

  val first = new BoolOr(new LeftGreaterThanRight(range,
                                                  new MaxValue(change)),
                         new LeftEqualRight(range, change))

  val second = new BoolAnd(new LeftGreaterThanRight(new NthValueFromPast(2,
                                                                         range),
                                                    new MaxValue(change)),
                           new BoolNot(new LeftGreaterThanRight(change,
                                                                new NthValueFromPast(2,
                                                                                     change))))

  println("first: " + first.name)
  println("second: " + second.name)

  val strategies = map(e => {
                         new InZoneWhileExpressionTrue(() => {
                                                         val tradeHandle = simulator.
                                                       }, e)
                       } )

  override def run() = {
    val runResult = super.run()

    runResult
  }

}

class RunBase(logging:ProbeRunner.Logging,
              config:RunConfig) extends ProbeRun {

  val priceAccessor = Price.Bid
  var currentTick:TimedTick = null


  val timer = new JustNowFakeTimer
  val timedLogger = new PrefixTimerTime(timer, logging.runLogger)

  val inputMetrics = TicksFileMetrics.fromFile(config.sourceTicks)

  val ticksSource = new TicksFromSequence(timer,
                                          Sequence.fromFile(config.sourceTicks,
                                                            MetatraderExportedTicks,
                                                            TicksBinaryCache),
                                          Run.Spread)
  ticksSource.tickEvent += (price => currentTick = new TimedTick(timer.currentTime, price))

  val periodsComposer =
    new Ticks2Periods(timer,
                      ticksSource.tickEvent,
                      config.period,
                      inputMetrics.firstTickTime.nextPeriodStartTime(config.period,
                                                                     config.periodStartShift))

  timer.callAt(inputMetrics.lastTickTime, timer.stop)

  val shift = Interval.days(29 * 9)  
  val endAt = inputMetrics.firstTickTime + shift

  println("start at: " + inputMetrics.firstTickTime)
  println("shift is: " + shift)
  println("end at: " + endAt)
  timer.callAt(endAt, timer.stop)

  def submitLog10Days():Unit = {

    val start = Time.now

    timer.callAt(timer.currentTime + Interval.days(5),
                 () => {
                   println("current time: " + timer.currentTime)
                   println("it took: " + (Time.now - start))
                   submitLog10Days()
                 } )
  }

  timer.callAt(inputMetrics.firstTickTime,
               submitLog10Days)

  override def run():Boolean = {
    timer.loop()

    true
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

  val SourceFile          = parameter(FileType,     "ticks",               "Source file with ticks to collect statistics from")
  val Period              = parameter(IntervalType, "period",              "Period size to work with")
  val PeriodStartShift    = parameter(IntervalType, "periodStartShift",    "Period's shift from normal period start point")
  val SlidingAverageSteps = parameter(IntType,      "slidingAverageSteps", "Count of steps to calculate sliding averages")
}


object Probes extends ProbeApp[RunConfig]("ProbesPP") {

  override def defaultPropertiesFileName = "probing.properties"

  def withParameter[T](parameter:Parameters.ParameterDefinition[T]):RunValue[T] =
    withParameter(parameter.theType,
                  parameter.name,
                  parameter.description)

  val _sourceFile          = withParameter(Parameters.SourceFile)
  val _period              = withParameter(Parameters.Period)
  val _periodStartShift    = withParameter(Parameters.PeriodStartShift)
  val _slidingAverageSteps = withParameter(Parameters.SlidingAverageSteps)

  def strategyName = "periods statistics collector"

  def createConfig = new RunConfig(_sourceFile.value,
                                   _period.value,
                                   _periodStartShift.value,
                                   _slidingAverageSteps.value)
}

object ProbesPP extends tas.probing.running.RunnersStarter("ProbesPPRunner")

object ProbesPPRunner extends ProbeRunner[RunConfig](new SearchForPredictors(_, _))
