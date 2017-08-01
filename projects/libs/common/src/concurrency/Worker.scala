package tas.concurrency

trait Worker {
  def run(func:()=>Unit):Unit
} 
