import java.io.File
import tas.raters.polygon.data.TestSet
import tas.raters.polygon.rater.DumbRater
import tas.raters.polygon.rater.ExternalRunRater

import tas.output.logger.ScreenLogger
import tas.utils.parsers.Arguments

import tas.utils.HelpTarget

object Polygon extends App {

  private val logger = ScreenLogger

  private def returnString(s:String) = s

  private val arguments = new Arguments(args) {
      def printHelp = {
        println("Testing polygon for the rater prototypes.")
        println("Mandatory agruments:")
        println("  root - the root of test set, where 'data' and 'tests' ")
        println("         directories located")
        println("  run  - the executable file to be started as testing prototype")
      }
    }

  private val root = new File(arguments.mandatoryParameter("root", returnString))

  val testSet = new TestSet(root)

  if ( ! testSet.validate(logger) ) {
    println("Have invalid tests, exiting.")
    sys.exit(1)
  }

  private val runFile = arguments.mandatoryParameter("run", returnString)

  private val rater = new ExternalRunRater(logger, runFile)

  testSet.executeFor(logger, rater)
}

class HelpImpl extends HelpTarget("previous day direction enter strategy",
                                  List("Polygon - this is polygon to test raters"))

object Help extends HelpImpl
object help extends HelpImpl
