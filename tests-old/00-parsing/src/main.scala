import tas.readers.PeriodsSequence
import tas.readers.PeriodReader


import java.io.FileOutputStream
import java.io.BufferedOutputStream
import java.io.PrintWriter

object TestMain extends App {

  val header = "<TICKER> <PER> <DATE> <TIME> <OPEN> <HIGH> <LOW> <CLOSE>"
  val prefix = "EURCHF 1 "
  
  val out = new PrintWriter(new BufferedOutputStream(new FileOutputStream("out/out.txt")))

  out.println(header)

  val periodsSource = PeriodsSequence.fromFile("out/source.txt")
  
  for (period <- periodsSource) {
    import period._
    import time._
  
    out.println(prefix
                + "%4d%02d%02d".format(year, month, day)
                + " "
                + "%02d%02d%02d".format(hours, minutes, seconds)
                + " %.5f".format(priceOpen)
                + " %.5f".format(priceMax)
                + " %.5f".format(priceMin)
                + " %.5f".format(priceClose))

  } 

  out.close()
} 
