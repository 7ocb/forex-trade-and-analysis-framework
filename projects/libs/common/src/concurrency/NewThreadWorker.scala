package tas.concurrency

class NewThreadWorker extends Worker {
  def run(func:()=>Unit) = {
    new Thread(new Runnable() {
      def run() = {
        func()
      } 
    } ).start()
  } 
} 
  

