package tas.probing.running

import java.io.File

import scala.collection.mutable.ListBuffer

import tas.utils.args.PropertyFileArguments

object RunnersStarter {

  val LimitCoresToUseParameter = "limitCoresToUse"
  val ConfigFileName = ".tas.probe-runners-starter.properties"


  lazy val coresCount = Runtime.getRuntime().availableProcessors()

  lazy val propertyFile = new File(System.getProperty("user.home"),
                                   ConfigFileName)

  lazy val coresUseLimitFromConfig = {
    val props = new PropertyFileArguments(propertyFile.getCanonicalPath())

    val limit = props.value(LimitCoresToUseParameter)
    if (limit.isDefined) {
      parseInt(limit.get,
               ifNonNumber = {
                 println("Warning, in properties file, " + LimitCoresToUseParameter + " property is no a number")
                 Int.MaxValue
               })
    } else {
      Int.MaxValue
    }
  }

  def parseInt(string:String, ifNonNumber: =>Int):Int = {
    try {
      string.toInt
    } catch {
      case nfe:NumberFormatException => {
        ifNonNumber
      }
    }
  }

}


class RunnersStarter(runnerClassName:String) extends App {

  private var runnersToStart = scala.math.min(Runtime.getRuntime().availableProcessors(),
                                              RunnersStarter.coresUseLimitFromConfig);
  
  private var serviceTarget:String = tryFindOutServiceAddress()
  private val _runners = new ListBuffer[RunnerUtility.ProcessHandle]
  // try to parse parameters

  if (parseParametes()) {

    println("RunnersStarter, spawning " + runnersToStart + " runners")

    for (index <- 1 to runnersToStart) {
      _runners += RunnerUtility.startClassAsProcess("runner: ",
                                                    runnerClassName,
                                                    serviceTarget)
    } 
    
  } else printHelp()

  def tryFindOutServiceAddress():String = {
    // TODO: not implemented now
    null
  } 
  
  def printHelp() = {
    println("Start several " + runnerClassName + " as runners")
    println("Arguments: <service.address:port> [runners_limit]")
    println("")
    println("  You an specify runners limit, to create less runners")
    println("than cpu cores available.")
  }

  def printHelpAndExit() {
    printHelp()
    sys.exit(1)
  }

  def parseParametes():Boolean = {
    if (args.length > 0) {
      if (args(0) == "help") {
        printHelp()
        return false
      }
      serviceTarget = args(0)
    }

    if (serviceTarget == null) {
      println("error: target server not specified")
      printHelp()
      return false
    } 

    if (args.length > 1) {
      runnersToStart =
        scala.math.min(RunnersStarter.parseInt(args(1),
                                               ifNonNumber = {
                                                 printHelpAndExit()
                                                 0
                                               } ),
                       runnersToStart)
    }

    if (args.length > 2) {
      println("extra arguments ignored")
    }

    return true
  } 
  
}

