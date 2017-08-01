package tas.utils

object repeat {
  def apply(times:Int)(body: => Unit) {
    (1 to times).foreach(_ => body)
  } 
} 
