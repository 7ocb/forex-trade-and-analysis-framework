package tas.hub.clients.connected

import tas.hub.HubProtocol

import tas.trading.{
  TradeRequest,
  TradeExecutor
}

import tas.{
  NotBound
}

import tas.types.Boundary

import scala.collection.mutable.ListBuffer

private [connected] class Executor(tradeRequest:TradeRequest,
                                   val messageHandler:(String)=>Unit,
                                   val onFreed:()=>Unit,
                                   sendPacket:HubProtocol.Packet=>Unit)
    extends TradeExecutor
    with NotBound {

  import HubProtocol._
  
  private var _closed = false
  
  private var _id:Long = InvalidId
  private val _whenConnected = new ListBuffer[() => Unit]

  def id = _id
  def connected(newId:Long):Unit = {
    if (_closed) return
    if (_id != InvalidId) throw new Error("Already connected!")

    _id = newId

    _whenConnected.foreach(_())
  }

  def externallyClosed = {
    if (! _closed ) {
      _onExtrnallyClosed()
      onClosed
    }
  }

  def opened = {
    if ( ! _closed) {
      _onOpened()
    }
  }
  
  private var _onOpened:()=>Unit = null
  private var _onExtrnallyClosed:()=>Unit = null

  sendPacket(new RequestNewId())
  
  def openTrade(stopValue:Boundary,
                takeProfit:Option[Boundary],
                onOpened:()=>Unit,
                onExternallyClosed:()=>Unit):Unit = {

    if (_onExtrnallyClosed != null
          || _onOpened != null) throw new Error("Open trade already called!")

    _onExtrnallyClosed = onExternallyClosed
    _onOpened = onOpened
    
    whenConnected {
      // open server-side executor
      sendPacket(new OpenTrade(_id, tradeRequest, stopValue, takeProfit))
    }
  }

  def closeTrade:Unit = {
    if (_onExtrnallyClosed == null && _onOpened == null) throw new Error("openTrade was not called!")
    whenConnected {
      _whenConnected.clear
      // close server-side
      sendPacket(new CloseRequest(_id))
      
      onClosed
    }
  }

  def setStop(stopValue:Boundary):Unit = whenConnected {
      // update server-side stop

      sendPacket(new UpdateStopRequest(_id, stopValue))
    }

  def setTakeProfit(takeProfit:Option[Boundary]):Unit = whenConnected {
      // update server-side stop

      sendPacket(new UpdateTakeProfitRequest(_id, takeProfit))
    }

  private def whenConnected(body: =>Unit):Unit = {
    if ( _closed ) return

    if (_id != InvalidId) {
      body
    } else {
      _whenConnected += (() => { body })
    }
  }

  private def onClosed {
    _closed = true
  }
}
