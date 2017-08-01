package tas.raters.polygon.rater

import scala.annotation.tailrec

import scala.collection.mutable.ListBuffer

import tas.concurrency.RunLoop
import tas.output.logger.{
  Logger,
  LogPrefix
}

import java.io.{
  File,
  BufferedReader,
  InputStreamReader,
  InputStream,
  IOException
}

class ExternalRunRater(externalLogger:Logger,
                       executableFile:String) extends Rater {

  private val raterLog = new LogPrefix("rater: ", externalLogger)

  private abstract class ThreadedProcess(onEnded:()=>Unit) {
    private var _ended = false
    private val _thread = createThread()
    _thread.start()

    protected def createThread():Thread

    protected def setEnded() = {
      if ( ! _ended ) {
        _ended = true
        onEnded()
      }
    }

    def isEnded = _ended
    def join() = _thread.join()
  }

  private class LineReader(runLoop:RunLoop, inputStream:InputStream, onLine:String=>Unit, onEnded:()=>Unit) extends ThreadedProcess(onEnded) {
    protected def createThread():Thread = new Thread() {

        private val reader = new BufferedReader(new InputStreamReader(inputStream))

        @tailrec override def run() = {
          val line = reader.readLine()

          if (line != null) {
            runLoop.post(() => {
                           onLine(line) })

            run()
          } else {
            reader.close()
            runLoop.post(setEnded)
          }
        }
      }
  }

  private class ProcessWaiter(runLoop:RunLoop, process:Process, onEnded:()=>Unit) extends ThreadedProcess(onEnded) {
    protected def createThread() = new Thread() {
      override def run() = {
        process.waitFor()
        runLoop.post(setEnded)
      }
    }
  }

  private class RateRunner(files:List[File]) {
    private val _result = ListBuffer[String]()

    private val _runLoop = new RunLoop()

    private val _subprocess = new ProcessBuilder((List(executableFile)
                                           ++ files.map(_.getPath()))
                                            :_*)
      .redirectErrorStream(false)
      .start()

    private val _workers = List(
        new LineReader(_runLoop,
                       _subprocess.getInputStream(),
                       line => {
                         _result += line
                       },
                       onEnded),

        new LineReader(_runLoop,
                       _subprocess.getErrorStream(),
                       line => {
                         raterLog.log(line)
                       },
                       onEnded),

        new ProcessWaiter(_runLoop,
                          _subprocess,
                          onEnded))

    _runLoop()

    def onEnded():Unit = {
      val allEnded = _workers.map(_.isEnded).reduce(_ && _)

      if (allEnded) {
        _workers.foreach(_.join)
        _runLoop.terminate()
      }
    }

    def result = _result.toList
  }

  def rate(files:List[File]):List[String] = new RateRunner(files).result

}
