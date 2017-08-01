package tas.trading

import tas.types.Boundary

// interface for the actual trading mechanism. 
trait TradeExecutor {

  def openTrade(stopValue:Boundary,
                takeProfitValue:Option[Boundary],
                onOpened:()=>Unit,
                onExternallyClosed:()=>Unit):Unit

  def closeTrade():Unit

  def setStop(stopValue:Boundary):Unit
  def setTakeProfit(takeProfitValue:Option[Boundary]):Unit
}

