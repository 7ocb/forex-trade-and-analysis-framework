package tas.trading

import tas.types.{
  Fraction,
  Trade,
  Boundary,
  Price
}
import tas.ActiveValue

object TradeParameters {
  def profit(tradeType:Trade,
             value:Fraction,
             openPrice:Fraction,
             closePrice:Fraction,
             comission:Fraction) = {

    val result = (closePrice - openPrice) * value

    tradeType * result - comission
  }

  def margin(value:Fraction, leverage:Fraction, price:Fraction) = value * price / leverage

}

sealed trait TradeValueType
case class TradeMargin(val margin:Fraction) extends TradeValueType {
  override def toString = "margin: " + margin
}
case class TradeValue (val value:Fraction) extends TradeValueType {
  override def toString = "value: " + value
}

trait OpenedTradeValues {
  val value:Fraction
  val openPrice:Fraction
  val tradeType:Trade

  def margin(leverage:Fraction):Fraction =
    TradeParameters.margin(value, leverage, openPrice)

  def profit(price:Price, comission:Fraction):Fraction =
    TradeParameters.profit(tradeType,
                           value,
                           openPrice,
                           tradeType.closePrice(price),
                           comission)
}

case class TradeResult(value:Fraction,
                       openPrice:Fraction,
                       closePrice:Fraction,
                       comission:Fraction,
                       tradeType:Trade) extends OpenedTradeValues {
  
  def profit = TradeParameters.profit(tradeType, value, openPrice, closePrice, comission)
}

case class TradeStatus(value:Fraction,
                       openPrice:Fraction,
                       tradeType:Trade) extends OpenedTradeValues {

  def close(price:Price, comission:Fraction):TradeResult =
    close(tradeType.closePrice(price),
          comission)

  def close(price:Fraction, comission:Fraction):TradeResult =
    new TradeResult(value,
                    openPrice,
                    price,
                    comission,
                    tradeType)
}

case class TradeRequest(value:Fraction,
                        tradeType:Trade,
                        delayUntil:Option[Boundary]) {

  def openWithPrice(price:Price):TradeStatus =
    openWithPrice(tradeType.openPrice(price))
  
  def openWithPrice(price:Fraction):TradeStatus =
    new TradeStatus(value,
                    price,
                    tradeType)

  override def toString = "" + tradeType + " request: " + value + ", delay: " + delayUntil
  override def equals(obj:Any):Boolean = {
    if ( !obj.isInstanceOf[TradeRequest] ) return false
    val a = obj.asInstanceOf[TradeRequest]

    return (a.value == value
              && a.tradeType == tradeType
              && a.delayUntil == delayUntil)
  }
}
