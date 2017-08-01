package tas

import tas.timers.Timer

abstract class ActiveExpresion[T](timer:Timer, recalculateMode:tas.ParameterSet.RecalculateMode) extends ActiveValue[T](timer) with Bound {
  protected val parameters = new ParameterSet(timer, recalculateMode)

  parameters.onUpdate {
    this << recalculate
  }

  def recalculate:T

  def unbindAll = parameters.unbindAll
} 
 
