package tas.trading.simulation.calculation

import tas.trading.TradeResult
import tas.types.{Time, Fraction}

class TradingStatus(val time:Time,
                    val balance:Fraction,
                    val equity:Fraction,
                    val margin:Fraction,
                    val freeMargin:Fraction,
                    val tradeResult:Option[TradeResult])
