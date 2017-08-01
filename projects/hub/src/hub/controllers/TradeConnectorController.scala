package tas.hub.controllers

import tas.types.Fraction

import tas.output.logger.Logger

import tas.service.ConnectionHandle

import scala.collection.mutable.ListBuffer

import tas.hub.clients.connected.ConnectedRemoteTradeBackend

import tas.hub.{
  BindClientConnectionToRemoteBackend,
  RelayChannel,
  HubProtocol
}

class TradeConnectorController(logger:Logger,
                               client:ConnectionHandle,
                               key:String,
                               onDisconnected:()=>Unit,
                               balance:Fraction,
                               equity:Fraction) extends RelayChannel.Controller {

  private def onDisconnect() {
    logger.log("Trade connector \"" + key + "\" is lost")
    client.close()
    _clients.foreach(_.close())
    onDisconnected()
  }

  val _tradeProviderBackend =
    new ConnectedRemoteTradeBackend(client,
                                    logger,
                                    balance,
                                    equity,
                                    onDisconnect _)

  private val _clients = new ListBuffer[BindClientConnectionToRemoteBackend]

  def addClient(client:ConnectionHandle):Unit = {
    client.sendRawData(HubProtocol.writePacket(new HubProtocol.TradeConnectorConnected(_tradeProviderBackend.balance,
                                                                                       _tradeProviderBackend.equity)))

    logger.log("Trade connector \"" + key + "\" got new connection")

    def onClientLost(binding:BindClientConnectionToRemoteBackend) = {
      logger.log("Trade connector \"" + key + "\" lost connection")

      client.close()
      _clients -= binding
    }

    _clients += new BindClientConnectionToRemoteBackend(logger,
                                                        client,
                                                        _tradeProviderBackend,
                                                        onClientLost _)
  }
}
