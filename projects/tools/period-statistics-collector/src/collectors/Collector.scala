package tas.periodstatisticscollector.collectors

trait Collector[T] {
  def value:Option[T]
  val name:String
}
