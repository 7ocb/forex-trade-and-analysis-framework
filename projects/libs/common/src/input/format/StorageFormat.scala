package tas.input.format

import java.io.InputStream
import java.io.OutputStream

trait StorageFormat[StoredType] {

  val name:String

  def reader(stream:InputStream):Reader[StoredType]

  def writer(stream:OutputStream):Writer[StoredType]
}
