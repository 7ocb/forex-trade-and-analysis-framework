package tas.probing.utils

import scala.annotation.tailrec

class VariantsIndex(_limits:List[Int]) {
  val _indexes = new Array[Int](_limits.size)

  if (_indexes.size == 0) throw new IllegalArgumentException("count of indexes is 0")

  _limits.foreach(limit => {
                    if (limit == 0) {
                      throw new IllegalArgumentException("one of limits is 0")
                    }
                  })

  var _overflow = false

  def reset() = for (x <- 0 to (_indexes.length - 1)) _indexes(x) = 0

  def increment() = {
    @tailrec def incrementSlot(slotIndex:Int):Unit = {
      if (slotIndex >= _indexes.length) _overflow = true
      else {
        val newValue = _indexes(slotIndex) + 1

        if (newValue >= _limits(slotIndex)) {
          _indexes(slotIndex) = 0
          incrementSlot(slotIndex + 1)
        } else {
          _indexes(slotIndex) = newValue
        }
      }
    }

    incrementSlot(0)
  }

  def slot(slotIndex:Int) = _indexes(slotIndex)

  def forSlots(action:(Int, Int)=>Unit) = {
    for (x <- 0 to (_indexes.length - 1)) {
      action(x, slot(x))
    }
  }

  def isOverflow = _overflow
}
