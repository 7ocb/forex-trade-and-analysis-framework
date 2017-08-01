package tas.hub.clients

import java.io.IOException

import tas.service.{
  ConnectionHandle,
  SocketConnectionHandle,
  Address
}

import tas.concurrency.RunLoop

import tas.trading.{
  TradeBackend,
  TradeRequest,
  TradeExecutor

}

import scala.collection.mutable.ListBuffer

import tas.hub.HubProtocol

import tas.output.warning.Warning
import tas.output.logger.{
  Logger,
  LogPrefix
}

import tas.{
  Bound,
  NotBound
}

import tas.types.{
  Interval,
  Fraction,
  Boundary
}

import tas.hub.clients.connected.ConnectedRemoteTradeBackend

object HubConnectionTradeBackend {
  val RetryInterval = Interval.seconds(5)
}

class HubConnectionTradeBackend(runLoop:RunLoop,
                                logger:Logger,
                                hubAddress:Address,
                                key:String) extends TradeBackend {
  import HubProtocol._

  private var _client:ConnectionHandle = null
  private var _tradeConnection:RemoteTradeBackend = null
  private var _executorsIds = 0

  startConnection()

  private def onDisconnected() = {
    if (isConnected) throw new RuntimeException("Lost connection to hub.")

    _client.close()
    _client = null

    retryConnectionLater()
  }

  private def retryConnectionLater() {
    runLoop.postDelayed(HubConnectionTradeBackend.RetryInterval,
                        startConnection _)
  }

  private def startConnection():Unit = {

    try {
      _client = SocketConnectionHandle.connect(runLoop,
                                               hubAddress)

      _client.setHandlers(onPacket = onPacketReceived _,
                          onDisconnect = onDisconnected _)

      _client.sendRawData(HubProtocol.writePacket(new HubProtocol.AskTradeConnector(key)))
    } catch {
      case ioe:IOException => retryConnectionLater()
    }
  }


  private def isConnected = _tradeConnection != null

  def newTradeExecutor(request:TradeRequest):(TradeExecutor with Bound) = {
    if ( ! isConnected ) throw new IllegalStateException("hub connection trading is not connected")

    val idOfThisExecutor = _executorsIds
    _executorsIds += 1

    val tradeLogger = new LogPrefix("Trade " + idOfThisExecutor + " (" + request.tradeType + "): ",
                                    logger)

    tradeLogger.log("requested: " + request)

    _tradeConnection.newTradeExecutor(request,
                                      tradeLogger.log(_),
                                      () => { /* do nothing when freed */})
  }

  private def ifConnected[T](body: => T) = if (isConnected) body
                                           else throw new RuntimeException("not connected")

  def balance:Fraction = ifConnected { _tradeConnection.balance }
  def equity:Fraction = ifConnected { _tradeConnection.equity }

  private def onPacketReceived(packet:Array[Byte]) {
    HubProtocol.readPacket(packet) match {

      case HubProtocol.TradeConnectorConnected(balance, equity) => {
        logger.log("Connected to hub trade backend")
        _tradeConnection =
          new ConnectedRemoteTradeBackend(_client,
                                          logger,
                                          balance,
                                          equity,
                                          () => throw new Error("lost connection to hub"))

        _tradeConnection.balanceMayBeChanged += (() => {
                                                   logger.log("new balance: " + _tradeConnection.balance)
                                                 })
      }

      case HubProtocol.ResourceNotFound() => onDisconnected()

      case wrongPacket => {
        logger.log("Hub sent wrong packet: ", wrongPacket)
      }
    }
  }


}
