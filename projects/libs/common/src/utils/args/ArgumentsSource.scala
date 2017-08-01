package tas.utils.args

trait ArgumentsSource {
  def sourceName:String
  def value(key:String):Option[String]
}


