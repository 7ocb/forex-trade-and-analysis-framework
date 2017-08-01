package tas.utils

import scala.reflect.ClassTag
import scala.collection.mutable.ListBuffer

final class SlidingValue[T](size:Int)(implicit m:ClassTag[T]) {
  private val data:Array[T] = new Array[T](size)

  private var writePtr = 0
  private var filled = false

  def +=(t:T):Unit = {
      data(writePtr) = t

      writePtr += 1
      if (writePtr >= size) {
        writePtr = 0
        filled = true
      }
    }

  def isFilled = filled

  def foldL[O](initial:O, folder:(O, T)=>O):O = {
    if (!isFilled) throw new RuntimeException("not filled")

    var o = initial

    for (i <- writePtr to (size - 1)) {
      o = folder(o, data(i))
    }

    for (i <- 0 to (writePtr - 1)) {
      o = folder(o, data(i))
    }

    o
  }

  def toList:List[T] = foldL(new ListBuffer[T](), (a:ListBuffer[T], v:T) => a += v).toList
}
