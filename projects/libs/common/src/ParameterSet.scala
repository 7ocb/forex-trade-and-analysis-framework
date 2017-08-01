package tas

import scala.collection.mutable.ListBuffer
import tas.timers.Timer
import tas.events.Event

object ParameterSet {
  abstract sealed class RecalculateMode
  object OnValueSet extends RecalculateMode
  object OnValueChanged extends RecalculateMode

  def newAlwaysRecalc(timer:Timer) = new ParameterSet(timer, OnValueSet)
  def newRecalcOnChange(timer:Timer) = new ParameterSet(timer, OnValueChanged)
} 

class ParameterSet(timer:Timer, recalculateMode:ParameterSet.RecalculateMode) extends Bound {

  private class Binding[T](val event:Event[T], val handler:(T)=>Unit) {
    def unbind = event -= handler
    def bind = event += handler
  } 

  private var _updatedAction: ()=>Unit = null
  private var _posted:Boolean = false

  private val _variables = new ListBuffer[ActiveValue[_]]
  private val _bindings = new ListBuffer[Binding[_]]

  private def eventToBind[T](value:ActiveValue[T]) = {
    recalculateMode match {
      case ParameterSet.OnValueSet => value.onValueSet
      case ParameterSet.OnValueChanged => value.onValueChanged
    } 
  } 
  
  def create[T]:ActiveValue[T] = {
    val value = new ActiveValue[T](timer)

    
    eventToBind(value) += (value => {
      if (! _posted
          && _updatedAction != null
          && isAllReady) {

        _posted = true
        
        timer.run {
          _posted = false
          _updatedAction()
        } 
      } 
    })
    
    _variables += value
    
    return value
  }

  def binding[T](event:Event[T]):ActiveValue[T] = {
    val value = create[T]

    val binding = new Binding(event, value.<<)
    
    binding.bind

    _bindings += binding
    
    value
  } 

  override def unbindAll = _bindings.foreach(_.unbind)
  
  def isAllReady = _variables.forall(_.isValue)

  def onUpdate(action: => Unit) = _updatedAction = action _
} 
