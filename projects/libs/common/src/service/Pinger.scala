package tas.service

import java.util.Arrays
import tas.types.Interval
import tas.concurrency.RunLoop

private object Pinger {
  val PingPacket = Array[Byte]()
}

private abstract class Pinger(runLoop:RunLoop,
                              interval:Interval,
                              timeout:Interval) {

  private var _pingTimeout:RunLoop.DelayedTask = null
  private var _pingRequest:RunLoop.DelayedTask = null

  repostTimeout()

  sendPing()
  postSendPing()
  


  def consumePing(buffer:Array[Byte]) = {
    if (Arrays.equals(buffer, Pinger.PingPacket)) {
      // it is ping
      repostTimeout()
      true
    } else false
  }

  private def repostTimeout() = {
    if (_pingTimeout != null) {
      _pingTimeout.cancel()
    }

    
    _pingTimeout = runLoop.postDelayed(timeout,
                                       onPingTimedOut _)
  }

  private def postSendPing():Unit = {
    _pingRequest = runLoop.postDelayed(interval,
                                       () => {
                                         sendPing()
                                         postSendPing()
                                       })
  }

  protected def sendPing()
  protected def onPingTimedOut()

  def stop() = {
    _pingTimeout.cancel()
    _pingRequest.cancel()
  }
}
