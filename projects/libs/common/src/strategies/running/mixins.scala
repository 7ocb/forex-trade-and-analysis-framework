package tas.strategies.running

import tas.concurrency.RunLoop

import tas.timers.{
  Timer,
  RealTimeTimer
}

import tas.output.logger.{
  Logger,
  PrefixTimerTime
}

trait WithRunLoop {
  val runLoop = new RunLoop
}

trait WithTimer {
  val timer:Timer
}

trait WithRealTimeTimer extends WithTimer { self:WithRunLoop => 
  override val timer = new RealTimeTimer(runLoop)
}

trait WithTimedLogger { self:WithTimer =>
  val logger:Logger

  lazy val timedLogger = timed(logger)

  def timed(baseLogger:Logger) = new PrefixTimerTime(timer, baseLogger)
}
