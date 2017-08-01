package tas.previousdaydirection.strategy

import tas.trading.{
  TradeType,
  Buy,
  Sell
}

sealed trait AllowedTradeTypes extends java.io.Serializable {
  def isAllowed(tradeType:TradeType):Boolean
}

object AllowedOnlyBuys extends AllowedTradeTypes {
  def isAllowed(tradeType:TradeType) = tradeType == Buy
  override def toString = "buys"
}

object AllowedOnlySells extends AllowedTradeTypes {
  def isAllowed(tradeType:TradeType) = tradeType == Sell
  override def toString = "sells"
}

object AllowedBoth extends AllowedTradeTypes {
  def isAllowed(tradeType:TradeType) = true
  override def toString = "both"
}
