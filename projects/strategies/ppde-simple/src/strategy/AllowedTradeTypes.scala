package tas.ppdesimple.strategy

import tas.types.{
  Trade,
  Buy,
  Sell
}

sealed trait AllowedTrades extends java.io.Serializable {
  def isAllowed(trade:Trade):Boolean
}

object AllowedOnlyBuys extends AllowedTrades {
  def isAllowed(trade:Trade) = trade == Buy
  override def toString = "buys"
}

object AllowedOnlySells extends AllowedTrades {
  def isAllowed(trade:Trade) = trade == Sell
  override def toString = "sells"
}

object AllowedBoth extends AllowedTrades {
  def isAllowed(trade:Trade) = true
  override def toString = "both"
}
