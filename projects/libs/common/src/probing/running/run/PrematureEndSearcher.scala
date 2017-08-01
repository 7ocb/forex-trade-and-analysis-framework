package tas.probing.running.run

import tas.timers.Timer

import tas.types.{
  Time,
  Fraction,
  Interval
}


object PrematureEndSearcher {
  case class PrematureEnd(message:String) extends Exception(message)

  trait Condition {
    val name:String
    def check():Boolean
  }

}

class PrematureEndSearcher(timer:Timer,
                           startTime:Time,
                           checkInterval:Interval,
                           conditions:List[PrematureEndSearcher.Condition]) {

  postCheck(startTime + checkInterval)

  private def postCheck(checkTime:Time):Unit = {
    timer.callAt(checkTime,
                 checkPrematureEnd)
  }

  private def checkPrematureEnd():Unit = {

    val metCondition = conditions.find(_.check())

    if (metCondition.isDefined) {
      throw new PrematureEndSearcher.PrematureEnd("Premature end condition met: " + metCondition.get.name)
    }

    postCheck(timer.currentTime + checkInterval)
  }

}
