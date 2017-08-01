package tas.previousdaydirection.strategy

import tas.{ActiveValue, NotBound, Bound}

import tas.trading.{TradeBackend, TradeRequest, TradeValue,
                    TradeHandle, CloseCondition,
                    TradeExecutor, Boundary}

import tas.timers.Timer

import scala.collection.mutable.ListBuffer

class TradeSet(timer:Timer, onExternallyClosedHandler: => Unit) {

  private val _stop = new ActiveValue[Boundary](timer) with NotBound
  private val _takeProfit = new ActiveValue[Option[Boundary]](timer) with NotBound

  private var _trades = new ListBuffer[TradeHandle]
  
  def closeAll() = {
    _trades.foreach(_.close())
    flushClosed()
  }

  def closePending() = {
    _trades.filter( ! _.isBecameOpened).foreach(_.close())
    flushClosed()
  }

  def countActiveTrades:Int = _trades.size

  def setBoundaries(stop:Boundary, takeProfit:Boundary) = {
    _stop << stop
    _takeProfit << Some(takeProfit)
  }

  def open(executor:TradeExecutor with Bound) = {
    val handle = new TradeHandle(new ActiveValue[CloseCondition](timer) with NotBound,
                                 _stop,
                                 _takeProfit,
                                 executor)
    handle.setObserver(new TradeHandle.Observer {
      def onExternallyClosed() = {
        _trades -= handle
        onExternallyClosedHandler
      } 
    })

    _trades += handle
  }

  private def flushClosed() = {
    _trades = _trades.filter( ! _.isBecameClosed)
  } 
}
