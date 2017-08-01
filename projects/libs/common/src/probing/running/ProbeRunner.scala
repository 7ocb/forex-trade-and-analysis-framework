package tas.probing.running

import java.io.Serializable
import tas.service.{
  ConnectionHandle,
  SocketConnectionHandle
}
import tas.service.AddressByAddress
import tas.concurrency.{
  RunLoop,
  ThreadPoolWorker
}
import tas.service.AddressByName
import java.io.File

import tas.output.logger.Logger
import tas.output.logger.FileLogger

import tas.probing.ProbingConfig

import tas.probing.running.run.ProbeRun

object ProbeRunner {
  val FAIL_POSTFIX = "_failed"

  class Logging(val runLogger:Logger, val combinedStatsLogger:Logger)
} 

abstract class ProbeRunner[ConfigType <: Serializable](createRun:(ProbeRunner.Logging, ConfigType) => ProbeRun) extends App {
  import ProbeRunner.Logging
  import tas.probing.running.Protocol

  private val worker = new ThreadPoolWorker()

  final def runProbe(config:ConfigType):Boolean = {
    val run = createRun(logging, config)

    run.run()
  }

  private val separatorLine = "-" * 80

  private var connection:ConnectionHandle = null
  val runLoop = new RunLoop


  if (args.length > 0) {
    if (args.length > 1) println("extra arguments ignored")


    val addressString = args(0)
    val addressParts = addressString.split(":")

    if (addressParts.length != 2) {
      malformedAddress(addressString)
    } else {
      println("started, binding to " + addressString)

      try {
        val port = addressParts(1).toInt

        connection = SocketConnectionHandle.connect(runLoop,
                                                    new AddressByName(addressParts(0),
                                                                      port),
                                                    new SocketConnectionHandle.Config(SocketConnectionHandle.DefaultConfig.pingInterval,
                                                                                      pingTimeout = ProbingConfig.ProbeConnectionTimeout))

        def onConnectionLost() = {
          println("lost connection to server")
          runLoop.terminate()
        }

        connection.setHandlers(onPacket = onServiceCommand _,
                               onDisconnect = onConnectionLost _)

        runLoop()
      } catch {
        case _:NumberFormatException => malformedAddress(addressString)
      }

    }

  } else {
    println("target address not specified, args.lenght == 0")
  }


  private def onServiceCommand(packet:Array[Byte]):Unit = {
    Protocol.readPacket(packet) match {
      case packet:Protocol.ServerToClientPacket => packet match {
        case Protocol.RunTask(log,
                              prefixPostfixLines,
                              config) => onNewTask(log,
                                                   prefixPostfixLines,
                                                   config.asInstanceOf[ConfigType])
        case Protocol.Terminate() => {
          println("received terminate")
          terminate()
        }
      }

      case _:Protocol.ClientToServerPacket => throw new RuntimeException("server sending client->server packet!")
    }
  }

  private var _logging:Logging = null
  
  protected def logging:Logging = _logging

  private class LoggerControl(val filename:String, pack:Boolean) {
    val logger = new FileLogger(filename,
      {
        if (pack) FileLogger.PackedContinous
        else FileLogger.PlainContinous
      } )

    def log(line:String) = logger.log(line)
    def close = logger.close

    def markFailed = new File(logger.fileName).renameTo(new File(logger.fileName + ProbeRunner.FAIL_POSTFIX))
  }

  private class WriteToBothLogger(one:Logger, two:Logger) extends Logger {
    def log(o:Any*) = {
      one.log((o.toList):_*)
      two.log((o.toList):_*)
    } 
  } 

  private def onNewTask(logPath:String, prefixPostfixLines:List[String], config:ConfigType) = {
    worker.run(() =>
      {
        try {
          val runLogger = new LoggerControl(logPath + ".run", true)
          val combinedStatsLogger = new LoggerControl(logPath + ".stats", false)

          val writeBothLogger = new WriteToBothLogger(runLogger.logger,
                                                      combinedStatsLogger.logger)

          _logging = new Logging(runLogger.logger,
                                 writeBothLogger)
          
          prefixPostfixLines.foreach(line => writeBothLogger.log(line))
          runLogger.log(separatorLine)
          runLogger.log("Probe log:")

          var succeed = runProbe(config)

          runLogger.log(separatorLine)
          prefixPostfixLines.foreach(line => runLogger.log(line))

          runLogger.close
          combinedStatsLogger.close

          if (! succeed) {
            runLogger.markFailed
            combinedStatsLogger.markFailed
          }

          runLoop.post(() => {
                         connection.sendRawData(Protocol.writePacket(new Protocol.TaskCompleted()))
                       } )
        } catch {
          case e:Throwable => {
            println("exception: " + e)
            e.getStackTrace().foreach(element => println("   " + element.toString()))
            println("exception in runner is fatal, terminating")
            terminate()
          }
        }
      })
  }

  private def terminate() {
    connection.close()
    runLoop.terminate()
  }

  private def malformedAddress(addr:String) = {
    println("malformed address: " + addr)
  }


}
