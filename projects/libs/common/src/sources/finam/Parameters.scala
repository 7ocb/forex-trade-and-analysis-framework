package tas.sources.finam

import tas.types.Time

object Parameters {
  sealed trait CurrencyPair
  object EUR_USD extends CurrencyPair
  object AUD_USD extends CurrencyPair
  object CHF_JPY extends CurrencyPair
  object EUR_CHF extends CurrencyPair
  object EUR_CNY extends CurrencyPair
  object EUR_GBP extends CurrencyPair
  object EUR_JPY extends CurrencyPair
  object EUR_RUB extends CurrencyPair
  object GBP_USD extends CurrencyPair
  object USD_CAD extends CurrencyPair
  object USD_CHF extends CurrencyPair
  object USD_CNY extends CurrencyPair
  object USD_DEM extends CurrencyPair
  object USD_JPY extends CurrencyPair
  object USD_RUB extends CurrencyPair

  sealed trait Interval
  object Min1 extends Interval
  object Min5 extends Interval
  object Min10 extends Interval
  object Min15 extends Interval
  object Min30 extends Interval
  object Hour extends Interval
  object Day extends Interval
  object Week extends Interval
  object Month extends Interval

  def dateFromTime(time:Time) = new Date(time.year, time.month - 1, time.day)

  sealed case class Date(val year:Int, val month:Int, val day:Int) {
    override def toString = "" + year + "-" + month + "-" + day
  } 
}   
