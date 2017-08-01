
import tas.utils.HelpTarget

import tas.utils.parsers.{
  Arguments,
  TimeParser
}

import tas.utils.IO

import tas.sources.finam.Parameters
import tas.sources.finam.FinamUrl
import tas.sources.finam.FinamPeriodsDownloader

import tas.types.{
  Time,
  Interval
}
import tas.types.Time.Moscow

import java.io.File
import java.io.BufferedWriter
import java.io.InputStream
import java.io.FileOutputStream
import java.io.OutputStreamWriter

import tas.types.{
  Period,
  PeriodBid
}

import tas.readers.PeriodsSequence

import tas.output.logger.ScreenLogger

import tas.concurrency.RunLoop

import tas.input.format.periods.text.PeriodsText

import scala.annotation.tailrec

import tas.utils.files.naming.{
  Periods,
  SourceFileName,
  FromBroker
}

object GetFinamHistory extends App {

  val logger = ScreenLogger

  val TimeZone_Finam = Moscow
  
  val Arg_StartDate = "from"
  val Arg_EndDate = "to"
  val Arg_Pair = "pair"
  val Arg_Help = "help"
  val Arg_OutDir = "outDir"

  class Pair(val key:String, val description:String, val pair:Parameters.CurrencyPair)

  val knownPairs = List(new Pair("eurusd", "EUR/USD", Parameters.EUR_USD),
                        new Pair("audusd", "AUD/USD", Parameters.AUD_USD),
                        new Pair("chfjpy", "CHF/JPY", Parameters.CHF_JPY),
                        new Pair("eurchf", "EUR/CHF", Parameters.EUR_CHF),
                        new Pair("eurcny", "EUR/CNY", Parameters.EUR_CNY),
                        new Pair("eurgbp", "EUR/GBP", Parameters.EUR_GBP),
                        new Pair("eurjpy", "EUR/JPY", Parameters.EUR_JPY),
                        new Pair("eurrub", "EUR/RUB", Parameters.EUR_RUB),
                        new Pair("gbpusd", "GBP/USD", Parameters.GBP_USD),
                        new Pair("usdcad", "USD/CAD", Parameters.USD_CAD),
                        new Pair("usdchf", "USD/CHF", Parameters.USD_CHF),
                        new Pair("usdcny", "USD/CNY", Parameters.USD_CNY),
                        new Pair("usddem", "USD/DEM", Parameters.USD_DEM),
                        new Pair("usdjpy", "USD/JPY", Parameters.USD_JPY),
                        new Pair("usdrub", "USD/RUB", Parameters.USD_RUB))
  
  val arguments = new Arguments(args) {
    def printHelp = {
      println("Utility to get UTC-timed minute data for requested data pair.")
      println("Mandatory arguments: ")
      println(" from=<date> - date from which to start download history ")
      println(" pair=<code> - code of pair values for which to download, can be: ")
      knownPairs.foreach(pair => println("  " + pair.key + " - " + pair.description))
      println("Optional arguments:")
      println(" outDir=<path>      - path to place output file, defaults to work directory")
      println(" to=<date>          - date up to which to download history (inclusive)")
      println("                      if omitted, today's date used")
      println("Alternatively, parameter 'help' can be specified to display this help.")
      println("Formats:")
      println(" Time format: " + TimeParser.sample)
    } 
  } 
  
  if (arguments.isProvided(Arg_Help)) {
    arguments.printHelp
    System.exit(0)
  } 

  val from = arguments.mandatoryParameter(Arg_StartDate,
                                          TimeParser.apply)

  val to = arguments.optionalParameter(Arg_EndDate,
                                       TimeParser.apply).getOrElse(Time.now)

  val pair = arguments.mandatoryParameter(Arg_Pair,
                                          str => knownPairs.find(_.key == str).get)

  val outFileName = SourceFileName(pair.key,
                                   new Periods(Interval.minutes(1)),
                                   from,
                                   to,
                                   new FromBroker("finam"))

  val outDirectory = arguments.optionalParameter(Arg_OutDir, str => str).getOrElse(".")

  val outFile = new File(outDirectory,
                         outFileName)

  logger.log("Out file name: " + outFile.toString)
  outFile.getParentFile().mkdirs()

  val output = PeriodsText.writer(IO.fileOutputStream(outFile))
  
  def fail(msg:String) = {
    logger.log("fail: " + msg)
    output.close()
    new File(outFileName).deleteOnExit()
    
    System.exit(1)
  } 

  def formatTime(time:Time) = "%d.%02d.%02d".format(time.year, time.month, time.day)

  RunLoop.withRunLoop(
    loop => {

      def shiftPeriodTime(original:PeriodBid) =
        new PeriodBid(original.bidOpen,
                      original.bidClose,
                      original.bidMin,
                      original.bidMax,
                      original.time.toUtcFrom(TimeZone_Finam))

      val periodsPortionHandler = new FinamPeriodsDownloader.Handler {
          def onPeriodsPortion(periods:List[PeriodBid]) = {

            logger.log("Got periods from " + periods.head.time + " to " + periods.last.time)

            // shift time and write to output
            periods.map(shiftPeriodTime).foreach(output.write)
          }

          def onNoMorePeriods = {
            loop.terminate()
          }
        }

      val downloader = new FinamPeriodsDownloader(loop,
                                                  ScreenLogger,
                                                  from,
                                                  to,
                                                  pair.pair,
                                                  periodsPortionHandler)
      logger.log("Ranges to download: " + downloader.leftRanges)

      downloader.start()
    } )
  
  output.close()

  logger.log("Completed.")
} 

class HelpImpl extends HelpTarget("previous day direction enter strategy",
                                  List("GetFinamHistory - target for dowloading finam history"))

object Help extends HelpImpl
object help extends HelpImpl 
