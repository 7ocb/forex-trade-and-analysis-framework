package tas.utils.parsers

import tas.types.Time

object TimeParser {
  def apply(str:String):Time = {
    val dateAndTime = str.split("/").map(_.trim)

    try {
      val timeWithDate = getDate(dateAndTime)

      shiftedWithTime(timeWithDate, dateAndTime)    
    } catch {
      case nfe:NumberFormatException => throw new FormatError("Can't parse: " + nfe)
    } 
  }

  val sample = "YYYY.MM.DD[/hour:min[:sec[.ms]]]"

  private def shiftedWithTime(time:Time, dateAndTime:Array[String]):Time = {
    if (dateAndTime.length < 2) return time

    val timeParts = dateAndTime(1).split(":").map(_.trim)

    if (timeParts.length < 2) throw new FormatError("Can't parse time - parts count < 2!")


    val hour = timeParts(0).toInt

    if (hour > 23) throw new FormatError("Wrong hour!")

    val minute = timeParts(1).toInt
    if (minute > 59) throw new FormatError("Wrong minute!")

    if (timeParts.length > 3) throw new FormatError("Can't parse time - extra parts at the end!")

    if (timeParts.length == 3) {
      val secMsec = timeParts(2).split("\\.").map(_.trim)

      val second = secMsec(0).toInt
      if (second > 59) throw new FormatError("Wrong second!")
      
      if (secMsec.length > 1) {

        val msec = secMsec(1).toInt
        if (msec > 999) throw new FormatError("Wrong msec!")

        time.shifted(shiftHours = hour,
                     shiftMinutes = minute,
                     shiftSeconds = second,
                     shiftMilliseconds = msec)
      } else {
        time.shifted(shiftHours = hour,
                     shiftMinutes = minute,
                     shiftSeconds = second)
      }  
    } else {
      time.shifted(shiftHours = hour,
                   shiftMinutes = minute)
    }  
  }

  private def getDate(dateAndTime:Array[String]):Time = {
    val date = dateAndTime(0)

    val dateParts = date.split("\\.").map(_.trim)
    
    if (dateParts.length != 3) throw new FormatError("Can't parse date - parts count != 3!")
    
    val year = dateParts(0).toInt

    val month = dateParts(1).toInt
    if (month < 1 || month > 12) throw new FormatError("Wrong month!")

    val day = dateParts(2).toInt

    Time.fromCalendar(year, month, day)
  }

} 
