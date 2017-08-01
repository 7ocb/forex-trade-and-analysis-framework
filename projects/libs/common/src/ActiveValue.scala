package tas

import tas.events.Event
import tas.timers.Timer

object ActiveValue {

  import scala.language.implicitConversions
  
  class OptionActiveValue[T](activeValue:ActiveValue[Option[T]]) {
    def valueOrNone = {
      if (activeValue.isValue) activeValue.value
      else None
    } 
  }

  implicit def activeValue2OptionActiveValue[T](activeValue:ActiveValue[Option[T]]) = new OptionActiveValue(activeValue)
  
} 

class ActiveValue[T] private (timer:Timer, initialState:Option[T]) {

  def this(timer:Timer) = this(timer, None)
  def this(timer:Timer, initialValue:T) = this(timer, Some(initialValue))
  
  private var _value = initialState
  private val _newValueEvent = Event.newAsync[T](timer)
  private val _valueSetEvent = Event.newAsync[T](timer)

  def << (t:T) = {
    val newValue = Option(t)
    val changed = _value != newValue
    _value = newValue

    _valueSetEvent << t
    
    if (changed) {
      _newValueEvent << t
    } 
  }

  def isValue = ! _value.isEmpty
  def value = _value.get

  def clear() = {
    _value = Option.empty[T]
  }

  def onValueChanged:Event[T] = _newValueEvent
  def onValueSet:Event[T] = _valueSetEvent
  
} 
