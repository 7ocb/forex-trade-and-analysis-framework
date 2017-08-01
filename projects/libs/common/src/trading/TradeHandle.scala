package tas.trading

import tas.types.Boundary

import tas.{Bound, ActiveValue}

// in case if condition detects that trade must be closed, it should set
// self to this value. As fast as condition ready and evaluates to
// CloseCondition, trade handle will try to close trade.
abstract sealed trait CloseCondition
object CloseTrade extends CloseCondition
object KeepTradeOpened extends CloseCondition

object TradeHandle {
  trait Observer {
    def onExternallyClosed()
  } 
}

import TradeHandle._

final class TradeHandle(condition:ActiveValue[CloseCondition] with Bound,
                        stopValue:ActiveValue[Boundary] with Bound,
                        takeProfitValue:ActiveValue[Option[Boundary]] with Bound,
                        tradeExecutor:TradeExecutor with Bound) {

  if (condition.isValue && condition.value == CloseTrade) throw new Error("Creating TradeHandle on condition == CloseTrade")
  
  private val conditionBinding:CloseCondition=>Unit = (value) => { if (value == CloseTrade) close() }
  private val stopValueBinding:Boundary=>Unit = tradeExecutor setStop _
  private val takeProfitValueBinding:Option[Boundary]=>Unit = tradeExecutor setTakeProfit _

  private var _observer:Option[Observer] = None
  
  private var _opened = false
  private var _closed = false

  condition.onValueChanged += conditionBinding
  stopValue.onValueChanged += stopValueBinding
  takeProfitValue.onValueChanged += takeProfitValueBinding

  // as initing, we will start request
  tradeExecutor.openTrade(stopValue.value, takeProfitValue.valueOrNone, onOpened, onExternallyClosed)

  
  def close():Unit = {

    if (_closed) return
  
    _closed = true

    tradeExecutor.closeTrade

    cleanup()
  }

  def isBecameOpened = _opened
  def isBecameClosed = _closed

  private def onOpened():Unit = {
    _opened = true
  }

  private def onExternallyClosed() = {
    _closed = true

    cleanup()
    
    _observer.foreach(_.onExternallyClosed())
  } 

  private def cleanup():Unit = {
    condition.unbindAll
    stopValue.unbindAll
    takeProfitValue.unbindAll    
    tradeExecutor.unbindAll

    condition.onValueChanged -= conditionBinding
    stopValue.onValueChanged -= stopValueBinding
    takeProfitValue.onValueChanged -= takeProfitValueBinding
  }

  def setObserver(observer:Observer) = {
    if (_observer != None) throw new RuntimeException("Observer for TradeHandle can not be reset, only set one time.")
    
    _observer = Some(observer)
  } 
}   
