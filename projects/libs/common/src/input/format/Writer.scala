package tas.input.format

trait Writer[StoredType] {
  def write(element:StoredType)

  def close()
}
