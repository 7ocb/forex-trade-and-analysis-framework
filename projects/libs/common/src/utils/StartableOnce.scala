package tas.utils

trait StartableOnce {

  private var started = false

  final def isStarted = started

  final def start():Unit = {
    if (started) return

    started = true

    doStart()
  }

  protected def doStart():Unit
}
