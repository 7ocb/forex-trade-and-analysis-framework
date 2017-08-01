package tas.output.logger

import tas.timers.Timer
import tas.output.format.Formatting

import java.io.{
  OutputStream,
  FileOutputStream,
  BufferedOutputStream,
  PrintStream,
  File
}

import java.util.zip.GZIPOutputStream  

trait Logger {
  def log(o:Any*)
}



object NullLogger extends Logger {
  override def log(o:Any*) = { /* just do nothing */ }
} 

class PrefixTimerTime(timer:Timer, slaveLogger:Logger) extends Logger {
  override def log(o:Any*) = slaveLogger.log((List(timer.currentTime, ": ") ++ o.toList):_*)
}

class LogPrefix(prefix:String, slaveLogger:Logger) extends Logger {
  override def log(o:Any*) = slaveLogger.log((prefix::o.toList):_*)
}

class CallToLog(func:(String)=>Unit) extends Logger {
  override def log(o:Any*) = func(Formatting.format(o:_*))
}

object ScreenLogger extends CallToLog(println) 

object FileLogger {
  sealed trait Behaviour {
    def isReopenForEachLine:Boolean
    def isPacked:Boolean

    private [FileLogger] val postfix = ""
  }

  object PackedContinous extends Behaviour {
    def isReopenForEachLine = false
    def isPacked = true

    override val postfix = ".gz"
  }

  object PlainContinous extends Behaviour {
    def isReopenForEachLine = false
    def isPacked = false
  }

  object PlainReopening extends Behaviour {
    def isReopenForEachLine = true
    def isPacked = false
  }

  // packed reopening is impossible for now

  val BufferSize = 1024*1024

}

class FileLogger(baseName:String, behaviour:FileLogger.Behaviour = FileLogger.PackedContinous) extends Logger {

  import FileLogger.BufferSize

  val fileName = baseName + behaviour.postfix

  private var _file:PrintStream = null

  private def file = {
    if (_file == null) {


      // init dir
      val dir = new File(fileName).getParentFile()
      if (dir != null) dir.mkdirs()

      //create file
      _file = new PrintStream({
        val buffered =
          wrapBuffer(new FileOutputStream(fileName, behaviour.isReopenForEachLine))

        if (behaviour.isPacked) wrapBuffer(new GZIPOutputStream(buffered))
        else buffered
      })
    }
    
    _file
  }

  private def wrapBuffer(stream:OutputStream):OutputStream = new BufferedOutputStream(stream,
                                                                                      FileLogger.BufferSize)

  override def log(o:Any*) = {
    file.println(Formatting.format(o:_*))
    if (behaviour.isReopenForEachLine) close()
  } 
  
  def close() {
    if (_file != null) {
      _file.close()
      _file = null
    }
  } 
} 

