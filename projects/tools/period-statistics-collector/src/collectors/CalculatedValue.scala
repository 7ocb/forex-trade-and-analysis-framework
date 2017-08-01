package tas.periodstatisticscollector.collectors

class CalculatedValue[T](val name:String,
                         calculate:()=>Option[T]) extends Collector[T] {
  def value = calculate()
}
