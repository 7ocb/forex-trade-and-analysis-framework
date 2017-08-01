package tas.paralleling

import java.io.InputStreamReader
import java.io.BufferedReader
import java.io.IOException
import scala.annotation.tailrec

import tas.utils.Runtime

object RunnerUtility {

  class ProcessHandle(val process:Process, val ioThread:Thread)
  
  def startClassAsProcess(prefix:String, classAndArgs:String*) = {
    val subprocess = { new ProcessBuilder((List("java", "-classpath",
                                                Runtime.classPathString)
                                           ++
                                           classAndArgs.toList):_*)
                      .redirectErrorStream(true)
                      .start() }
    
    val ioThread = new Thread() {
      override def run() = {

        
        try {
          val ioChannel = new BufferedReader(new InputStreamReader(subprocess.getInputStream()))
          @tailrec def pipe():Unit = {
            val line = ioChannel.readLine
            if (line != null) {
              println(prefix + line)
              pipe()
            } 
          }

          pipe()

        } catch {
          case e:IOException => {
            // ignore, child terminated
          }
        } 
      }
    }

    ioThread.start()
    new ProcessHandle(subprocess,
                      ioThread)
  } 
  
} 
