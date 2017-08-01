
import java.io.File

import tas.types.Time
import tas.types.Fraction

import tas.paralleling.{
  Controller,
  RunnersStarter,
  Action
}

import tas.prediction.zones.{
  Zone,
  ZonesSet,
  PriceZoneTracker,
  ComplementSearch
}

import tas.input.Sequence
import tas.input.format.ticks.metatrader.MetatraderExportedTicks
import tas.input.format.ticks.bincache.TicksBinaryCache

import tas.prediction.InZoneWhileExpressionTrue

import tas.prediction.search.value.Comparsions
import tas.prediction.search.equivalency.Equivalency
import tas.prediction.search.equivalency.Equivalency.{
  Slot,
  ConvertSlots
}

import tas.concurrency.RunLoop

import tas.prediction.search.value.{
  PeriodField,
  ValueFactory,
  ConstantValue,
  Multiply,
  NthValueFromPast,
  MinValue,
  MaxValue,
  NormalizedByMax,
  BoolOr,
  BoolNot,
  BoolAnd,
  SlidingMax,
  SlidingMin
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

case class Input(periods:PeriodSource)

object Run {
  type VF[Type] = ValueFactory[Type, Input]

  val Spread = Fraction("0.0002")
}

class SlotsToExpressions(values:List[Run.VF[Boolean]]) extends Equivalency.ConvertSlots[Run.VF[Boolean]] {
  import Run.VF

  def convertAnd(left:Slot, right:Slot):VF[Boolean] = new BoolAnd(convert(left), convert(right))
  def convertOr(left:Slot, right:Slot):VF[Boolean] = new BoolOr(convert(left), convert(right))
  def convertNot(sub:Slot):VF[Boolean] = new BoolNot(convert(sub))
  def convertValue(index:Int):VF[Boolean] = values(index)
}

class RunBase {

  var currentTick:TimedTick = null

  // val sourceFile = new File("/home/elk/work/tas/data/duka-eurusd-ticks-2012-01-01---2012-12-31-from-periods-93703.txt")
  val sourceFile = new File("/home/elk/work/tas/data/dukas-eurusd-ticks-2012+2013.txt")


  val period = Interval.hours(1)
  val periodStartShift = Interval.ZERO


  val timer = new JustNowFakeTimer

  val inputMetrics = TicksFileMetrics.fromFile(sourceFile)

  val ticksSource = new TicksFromSequence(timer,
                                          Sequence.fromFile(sourceFile,
                                                            MetatraderExportedTicks,
                                                            TicksBinaryCache),
                                          Run.Spread)
  ticksSource.tickEvent += (price => currentTick = new TimedTick(timer.currentTime, price))

  val periodsComposer =
    new Ticks2Periods(timer,
                      ticksSource.tickEvent,
                      period,
                      inputMetrics.firstTickTime.nextPeriodStartTime(period,
                                                                     periodStartShift))

  timer.callAt(inputMetrics.lastTickTime, timer.stop)

  def run() = timer.loop()

}

case class Result(result:List[(ZonesSet, ZonesSet)]) extends java.io.Serializable

class Run(expressions:List[ValueFactory[Boolean, Input]]) extends RunBase {

  val input = new Input(periodsComposer)

  val trackers =
    expressions
      .map(factory => {
             val e = factory.create(input)
             val tracker = new PriceZoneTracker(() => currentTick)
             new InZoneWhileExpressionTrue(() => tracker.enter().leave,
                                           e)
             tracker
           })

  def results = trackers.map(_.results())

}

object PriceAccess {
  val access = Price.Bid
}

object Paralleled extends App {
  import Run.VF
  type Const[T] = ConstantValue[T, Input]

  def createExpressions() = {
    val booleanPermutationsDeep = 2
    val nthFromPast = 2
    val multiplier = Fraction("0.85")

    val booleanPermutationsSlots = Equivalency.uniquePermutations(booleanPermutationsDeep)

    // println("pre pa: " + priceAccessor)

    val inputFractions = List(new PeriodField[Fraction, Input](_.periods,
                                                               "period range",
                                                               _.range(PriceAccess.access)),
                              new PeriodField[Fraction, Input](_.periods,
                                                               "period change",
                                                               _.change(PriceAccess.access)
                                                                 ))

    def andModifiedBy[T](func:VF[T]=>List[VF[T]]) = (original:List[VF[T]]) => {
        original ++ original.flatMap(func)
      }

    val comparsionGroups = List(andModifiedBy((e:VF[Fraction]) => List(
                                                new SlidingMax(100, e),
                                                // new SlidingMin(100, e),
                                                new Multiply(e,
                                                             new NormalizedByMax(e)),
                                                new Multiply(e,
                                                             new Const(multiplier)),
                                                new Multiply(e,
                                                             new Const(Fraction("0.85"))),
                                                new NthValueFromPast(5,
                                                                     e),
                                                new NthValueFromPast(2,
                                                                     e)
                                              ))
                                             (inputFractions),
                                inputFractions.map(e => new NormalizedByMax(e)))



    comparsionGroups
      .flatMap(e => Comparsions.intercompareAll(e))
      .combinations(booleanPermutationsDeep)
      .flatMap(combination => {
                 val converter = new SlotsToExpressions(combination)
                 booleanPermutationsSlots.map(converter.convert)
               } ).toList

  }

  val beforeCreatingExpressions = Time.now
  var expressions = createExpressions()
  println("expressions count: " + expressions.size)

  println("Creating expressions took: " + (Time.now - beforeCreatingExpressions))

  val runLoop = new RunLoop()

  val controller = new Controller(runLoop)

  val dispatchCount = 100


  var calculating = 0
  val beforeWholeCalc = Time.now

  var storage = List[(ZonesSet, ZonesSet)]()

  def dispatchNext() = {
    if (! expressions.isEmpty) {

      val tasks = expressions.take(dispatchCount)
      expressions = expressions.drop(dispatchCount)

      calculating += tasks.size

      println("dispached " + calculating + " tasks")

      controller.submit(new Action[Result] {
                          def run():Result = {
                            val beforeCalc = Time.now

                            val r = new Run(tasks)
                            r.run()
                            val out = new Result(r.results)


                            println("calc took: " + (Time.now - beforeCalc))
                            out
                          }
                        },
                        ((got:Result) => {
                           storage = got.result ++ storage
                           println("got data, saving")
                         } ))
    }
  }

  def onIdle() = {
    println("Whole calculation took: " + (Time.now - beforeWholeCalc))
    controller.shutdown()
    runLoop.terminate()
  }

  controller.onHaveFreeResources += dispatchNext

  controller.onBecomeIdle += onIdle

  RunnersStarter.startRunners("0.0.0.0:9000", None)

  runLoop()

}


// Int
// -------
// (expressions:List[ValueFactory[Boolean, Input]])
// -------
// run
// -------
// // import tas.input.format.ticks.metatrader.MetatraderExportedTicks
// // import tas.input.format.ticks.bincache.TicksBinaryCache
// -------
// // import tas.input.Sequence
// -------
// import tas.probing.running.run.ProbeRun

// import tas.probing.types.{
//   Type,
//   IntType,
//   FileType,
//   IntervalType
// }


// -------


// -------
// import tas.probing.{
//   ProbeApp,
//   RunValue
// }

// -------

// // import tas.events.{
// //   Subscription,
// //   SyncCallSubscription
// // }

// // import tas.input.Sequence
// // import tas.input.format.ticks.metatrader.MetatraderExportedTicks
// // import tas.input.format.ticks.bincache.TicksBinaryCache

// -------
// // import tas.periodstatisticscollector.collectors.{
// //   Collector,
// //   PeriodValue,
// //   CalculatedValue,
// //   PeriodWay,
// //   SlidingAverageCollector,
// //   DeltaCollector
// // }



// -------
//   // val shift = Interval.days(29 * 9)  
//   // val endAt = inputMetrics.firstTickTime + shift

//   // println("start at: " + inputMetrics.firstTickTime)
//   // println("shift is: " + shift)
//   // println("end at: " + endAt)
//   // timer.callAt(endAt, timer.stop)

//   // def submitLog10Days():Unit = {

//   //   val start = Time.now

//   //   timer.callAt(timer.currentTime + Interval.days(5),
//   //                () => {
//   //                  println("current time: " + timer.currentTime)
//   //                  println("it took: " + (Time.now - start))
//   //                  submitLog10Days()
//   //                } )
//   // }

//   // timer.callAt(inputMetrics.firstTickTime,
//   //              submitLog10Days)

//   // override def run():Boolean = {
//   //   timer.loop()

//   //   true
//   // }

//   // timer.loop()

// -------
//   // val timedLogger = new PrefixTimerTime(timer, logging.runLogger)


// -------


// -------
// //   val SlidingAverageSteps = parameter(IntType,      "slidingAverageSteps", "Count of steps to calculate sliding averages")
// // }


// // object SearchPrediction extends ProbeApp[RunConfig]("SearchPredictionRunnersStarter") {

// //   override def defaultPropertiesFileName = "searcher.properties"

// //   def withParameter[T](parameter:Parameters.ParameterDefinition[T]):RunValue[T] =
// //     withParameter(parameter.theType,
// //                   parameter.name,
// //                   parameter.description)

// //   val _sourceFile          = withParameter(Parameters.SourceFile)
// //   val _period              = withParameter(Parameters.Period)
// //   val _periodStartShift    = withParameter(Parameters.PeriodStartShift)
// //   val _slidingAverageSteps = withParameter(Parameters.SlidingAverageSteps)


// //   def strategyName = "periods statistics collector"

// //   def createConfig = new RunConfig(_sourceFile.value,
// //                                    _period.value,
// //                                    _periodStartShift.value,
// //                                    _slidingAverageSteps.value)
// // }

// // object SearchPredictionRunnersStarter extends tas.probing.running.RunnersStarter("SearchPredictionProbeRunner")

// // object SearchPredictionProbeRunner extends ProbeRunner[RunConfig](new SearchForPredictors(_, _))

// -------

// // class SearchForPredictors(logging:ProbeRunner.Logging,
// //                           config:RunConfig) extends RunBase(logging, config) {
// //   val booleanPermutationsDeep = 2
// //   val nthFromPast = 2
// //   val multiplier = Fraction("0.85")

// //   val booleanPermutationsSlots = Equivalency.uniquePermutations(booleanPermutationsDeep)

// //   val inputFractions = List(new PeriodField(periodsComposer,
// //                                             "period range",
// //                                             _.range(priceAccessor)),
// //                             new PeriodField(periodsComposer,
// //                                             "period change",
// //                                             _.change(priceAccessor)))

// //   val comparsionGroups = List(andModifiedBy((e:Value[Fraction]) => List(
// //                                               // new MinValue(e),
// //                                               new SlidingMax(100, e),
// //                                               // new Multiply(e,
// //                                               //              new NormalizedByMax(e)),
// //                                               // new Multiply(e,
// //                                               //              new Constant(multiplier)),
// //                                               // new Multiply(e,
// //                                               //              new Constant(Fraction("0.85"))),
// //                                               // new NthValueFromPast(nthFromPast,
// //                                               //                      e),
// //                                               new NthValueFromPast(2,
// //                                                                    e)
// //                                             ))
// //                                            (inputFractions),
// //                               inputFractions.map(e => new NormalizedByMax(e)))

// //   def andModifiedBy[T](func:Value[T]=>List[Value[T]]) = (original:List[Value[T]]) => {
// //       original ++ original.flatMap(func)
// //   }

// //   val expressions = comparsionGroups
// //     // .map()
// //     .flatMap(e => Comparsions.intercompareAll(e))
// //     .combinations(booleanPermutationsDeep)
// //     .flatMap(combination => {
// //                val converter = new SlotsToExpressions(combination)
// //                booleanPermutationsSlots.map(converter.convert)
// //              } ).toList

// //   // println("expressions: ")
// //   // expressions.foreach(e => println("" + e.name))
// //   // println("expressions count: " + expressions.length)
// //   println("expressions count: " + expressions.length)

// //   val trackers = expressions.map(e => {
// //                                    val tracker = new PriceZoneTracker(() => currentTick)
// //                                    new InZoneWhileExpressionTrue(() => tracker.enter().leave,
// //                                                                  e)
// //                                    (tracker, e)
// //                                  } )

// //   override def run() = {
// //     val runResult = super.run()

// //     // nextPeriodPredictors.foreach(_.dumpTo(logging.combinedStatsLogger))

// //     val zones = trackers
// //       .flatMap(a => {
// //                  val results = a._1.results
// //                  List((results._1, a._2),
// //                       (results._2, a._2))
// //                    })

// //     val positiveZones = zones.filter(_._1.change > 0)


// //     def findForZonesCount(min:Int, max:Int) = {
// //       val withRequiredZonesCount = positiveZones.filter(a => (a._1.count >= min) && (a._1.count < max))

// //       if (!withRequiredZonesCount.isEmpty) {

// //         val top = withRequiredZonesCount
// //           .sortWith(_._1.change > _._1.change)
// //           .sortWith(_._1.rating > _._1.rating)
// //           .head
// //           // .maxBy(_._1.rating)

// //         println("top with " + min + " <= zones < " + max)
// //         println("rating is: " + top._1.rating)
// //         println("probing ended, way is: " + top._1.change)
// //         println("probing ended, its a way of expression: " + top._2.name)

// //         // def way(accessor:(PriceZoneTracker.CompletedZone=>Fraction)) = top
// //         //   ._2
// //         //   .zones
// //         //   .map(accessor)
// //         //   .foldLeft(List(Fraction.ZERO))((a, c) => { (a.head + c) :: a } )
// //         //   .reverse
// //         //   .toList

// //         println("way is: " + top._1.way.toList)
// //         // println("up way: " + way(_.upDiff))
// //         // println("down way: " + way(_.downDiff))

// //         println("zones count is: " + top._1.count)

// //       } else {
// //         println("no probes with more than " + min + " <= zones < " + max)
// //       }
// //     }

// //     findForZonesCount(10, 50)
// //     findForZonesCount(50, 100)
// //     findForZonesCount(100, 200)
// //     findForZonesCount(200, 500)
// //     findForZonesCount(500, 1000)
// //     findForZonesCount(1000, 100000)

// //     val zonesForComplementSearching = zones
// //       .filter(_._1.count > 50)
// //       .map(a => new ComplementSearch.Qualified(a._1, a._2))


// //     def findComplements(level:Int) = {

// //       def sortValue(a:ZonesSet) = a.count

// //       val complements =
// //         new ComplementSearch(level,
// //                              zonesForComplementSearching)
// //           .complements
// //           .sortWith((l, r) => (sortValue(l._1.zones) + sortValue(l._2.zones)) > (sortValue(r._1.zones) + sortValue(r._2.zones)))


// //       // complements.pepe

// //       // case class WithFootprint(val result:Zones, val expression:Value[Boolean]) {
// //       //   lazy val footprint = result.footprint(level)
// //       // }

// //       // val complements = zonesForComplementSearching
// //       //   .filter(a => a._1.rating < level)
// //       //   .map(a => new WithFootprint(a._1, a._2))
// //       //   .combinations(2)
// //       //   .map(a => (a(0).footprint + a(1).footprint, (a(0), a(1))))
// //       //   .filter(a => !a._1.haveFails)
// //       //   .toList
// //       //   .sortWith(_._1.change > _._1.change)
// //       // .ma
// //       // .toList
// //       // .sortWith(_._1.change > _._1.change)

// //       println("found " + complements.size + " complements for level " + level)

// //       if (! complements.isEmpty) {
// //         println("showing top 10")

// //         val top10 = complements.take(10)

// //         // val pair = complements.head
// //         val summaryTimedChanges = for (pair <- top10) yield {

// //           println("pair's change: " + (pair._1.zones.change + pair._2.zones.change))
// //           println("it's zones count: " + (pair._1.zones.count + pair._2.zones.count))
// //           println("it is pair: ")
// //           println("1: " + pair._1.zones.direction + " when " + pair._1.value.name)
// //           println("it's way: " + pair._1.zones.way.toList)
// //           println("2: " + pair._2.zones.direction + " when " + pair._2.value.name)
// //           println("it's way: " + pair._2.zones.way.toList)

// //           val summaryTimedChanges = (pair._2.zones.timedChanges.toList ++ pair._1.zones.timedChanges.toList)

// //           val summaryWay = (ZonesSet.wayFromChanges(summaryTimedChanges
// //                                                       .sortBy(_.time)
// //                                                       .map(_.value)
// //                                                       .toArray)
// //                               .toList)

// //           println("it's summary way: " + summaryWay)

// //           summaryTimedChanges
// //         }

// //         println("whole summary way for top complements for level " + level + ":")
// //         println("way: " + ZonesSet.wayFromChanges(summaryTimedChanges
// //                                                     .reduce(_ ++ _)
// //                                                     .sortBy(_.time)
// //                                                     .map(_.value)
// //                                                     .toArray)
// //                   .toList)

// //       }
// //     }

// //     val startComplementSearching = Time.now

// //     // findComplements(3)
// //     findComplements(4)
// //     findComplements(5)
// //     findComplements(6)
// //     findComplements(7)

// //     // }
// //     println("Complement searching took: " + (Time.now - startComplementSearching))

// //     runResult
// //   }

// // }

// // class RunBase(logging:ProbeRunner.Logging,
// //               config:RunConfig) extends ProbeRun {

// //   val priceAccessor = Price.Bid
// //   var currentTick:TimedTick = null


// //   val timer = new JustNowFakeTimer
// //   val timedLogger = new PrefixTimerTime(timer, logging.runLogger)

// //   val inputMetrics = TicksFileMetrics.fromFile(config.sourceTicks)

// //   val ticksSource = new TicksFromSequence(timer,
// //                                           Sequence.fromFile(config.sourceTicks,
// //                                                             MetatraderExportedTicks,
// //                                                             TicksBinaryCache),
// //                                           Run.Spread)
// //   ticksSource.tickEvent += (price => currentTick = new TimedTick(timer.currentTime, price))

// //   val periodsComposer =
// //     new Ticks2Periods(timer,
// //                       ticksSource.tickEvent,
// //                       config.period,
// //                       inputMetrics.firstTickTime.nextPeriodStartTime(config.period,
// //                                                                      config.periodStartShift))

// //   timer.callAt(inputMetrics.lastTickTime, timer.stop)

// //   // val shift = Interval.days(29 * 9)  
// //   // val endAt = inputMetrics.firstTickTime + shift

// //   // println("start at: " + inputMetrics.firstTickTime)
// //   // println("shift is: " + shift)
// //   // println("end at: " + endAt)
// //   // timer.callAt(endAt, timer.stop)

// //   def submitLog10Days():Unit = {

// //     val start = Time.now

// //     timer.callAt(timer.currentTime + Interval.days(5),
// //                  () => {
// //                    println("current time: " + timer.currentTime)
// //                    println("it took: " + (Time.now - start))
// //                    submitLog10Days()
// //                  } )
// //   }

// //   timer.callAt(inputMetrics.firstTickTime,
// //                submitLog10Days)

// //   override def run():Boolean = {
// //     timer.loop()

// //     true
// //   }
// // }

// // object Parameters {
// //   case class ParameterDefinition[T](val theType:Type[T],
// //                                     val name:String,
// //                                     val description:String)

// //   def parameter[T](theType:Type[T],
// //                    name:String,
// //                    description:String):ParameterDefinition[T] = new ParameterDefinition(theType,
// //                                                                                         name,
// //                                                                                         description)

// //   val SourceFile          = parameter(FileType,     "ticks",               "Source file with ticks to collect statistics from")
// //   val Period              = parameter(IntervalType, "period",              "Period size to work with")
// //   val PeriodStartShift    = parameter(IntervalType, "periodStartShift",    "Period's shift from normal period start point")

// -------
// // case class RunConfig(sourceTicks:File,
// //                      period:Interval,
// //                      periodStartShift:Interval,
// //                      slidingAverageSteps:Int)


// -------


// -------
// // import tas.probing.running.{
// //   ProbeRunner,
// //   RunnersStarter
// // }

// -------
// // case class RunConfig(sourceTicks:File,
// //                      period:Interval,
// //                      periodStartShift:Interval,
// //                      slidingAverageSteps:Int)


// -------
// // object Mods {
// //   object Min extends Modificator[Fraction] {
// //     def apply(value:Value[Fraction]) = new MinValue(value)

// //     def isDeniedAfter
// //   }
// // }

// -------
// postExit
// -------
// se
// -------
// List[Int]
// -------
// List[Int]
// -------
// List[Int]
// -------
// List[Int]
// -------
// [List[Int]]
// -------
// prediction
// -------
// dependency
// -------
//   <dependency>../../libs/prediction</dependency>
// -------
//                           (got => println("got " + got))

// -------
// find
// -------
// HaveFreeResources
// -------
//   private val _onHaveFreeResources = new SyncCallSubscription
// -------
// all
// -------
// have 
// -------
// exzpre
// -------
// Footprint
// -------
// val
// -------
// next
// -------


// -------
//   // val logger = new FileLogger("expressions", FileLogger.PlainContinous)



// -------
// Plain
// -------
// log
// -------
// file
// -------
// siz
// -------
// Hundred
// -------
//   val 
// -------
// tas.paralleling
// -------
// predictions-test/src/strategy/
// -------
// Max
// -------
//   SlidingMax
// -------
//                                                 new SlidingMax(100, e),

// -------
//                                                 new SlidingMax(100, e),
// -------
// Max
// -------
//                                                 new SlidingMax(100, e),
// -------
//                                                 new MinValue(e),
//                                                 new MinValue(e),

// -------
//                                                 new MinValue(e),
// -------
// ConstantValue
// -------
// ConstantValue
