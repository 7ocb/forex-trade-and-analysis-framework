package tas.sources.finam

object FinamUrl {

  import Parameters._


  val serverUrl = "195.128.78.52"
  
  def apply(pair:CurrencyPair, interval:Interval, fromDate:Date, toDate:Date) = {
    ("http://" + serverUrl
     + "/EURUSD_130214_130216.txt?market=5"
     + "&em=" + currencyPair(pair)
     + "&code=EURUSD"
     + "&df=" + fromDate.day
     + "&mf=" + fromDate.month
     + "&yf=" + fromDate.year
     + "&dt=" + toDate.day
     + "&mt=" + toDate.month
     + "&yt=" + toDate.year
     + "&p=" + intervalCode(interval)
     + "&f=EURUSD_130214_130216&e=.txt&cn=EURUSD&dtf=1&tmf=1&MSOR=0&mstimever=0&sep=5&sep2=1&datf=2&at=1")
  }

  private def currencyPair(pair:CurrencyPair) = pair match {
    case EUR_USD => 83
      case AUD_USD => 66699
      case CHF_JPY => 21084
      case EUR_CHF => 106
      case EUR_CNY => 83226
      case EUR_GBP => 88
      case EUR_JPY => 84
      case EUR_RUB => 66860
      case GBP_USD => 86
      case USD_CAD => 66700
      case USD_CHF => 85
      case USD_CNY => 83225
      case USD_DEM => 82
      case USD_JPY => 87
      case USD_RUB => 901
  }

  private def intervalCode(interval:Interval) = interval match {
    case Min1 => 2
    case Min5 => 3
    case Min10 => 4
    case Min15 => 5
    case Min30 => 6
    case Hour => 7
    case Day => 8
    case Week => 9
    case Month => 10
  } 
} 
