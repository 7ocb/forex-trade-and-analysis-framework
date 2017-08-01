package tas.concurrency

import java.util.concurrent.Executors

class ThreadPoolWorker(threadsCount:Int) extends Worker {

  def this() = this(1)

  private val executors = Executors.newFixedThreadPool(threadsCount)

  def run(func:()=>Unit) = {
    executors.execute(new Runnable() {
                        def run() = {
                          func()
                        }
                      })
  } 
} 
  

