package tas.input.format

object Reader {
  case class FormatError(val message:String) extends Exception(message)
}

trait Reader[StoredType] {

  def read():Option[StoredType]

  def close()
}
