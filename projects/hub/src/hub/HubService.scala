package tas.hub

import scala.collection.mutable.{
  Map,
  ListBuffer
}

import tas.output.logger.Logger

import tas.concurrency.RunLoop

import tas.service.{
  Address,
  Service,
  ConnectionHandle
}

import tas.hub.controllers.{
  TickProviderController,
  TradeConnectorController
}

import tas.types.Fraction

object HubService {
  private var clientId = 0;
  def nextClientId = {
    clientId += 1
    clientId
  }
}


class HubService(runLoop:RunLoop,
                 logger:Logger,
                 bindAddress:Address) {

  private val tradeConnectorLogger = logger
  // private val tradeConnectorLogger = NullLogger

  private val serviceConnection = new Service(runLoop,
                                              bindAddress,
                                              onNewConnection)

  private val _tickProviderControllerFactory = new RelayChannel.ControllerFactory {
      def create(logger:Logger,
                 client:ConnectionHandle,
                 key:String,
                 onDisconnected:()=>Unit) = new TickProviderController(logger,
                                                                       client,
                                                                       key,
                                                                       onDisconnected)
    }

  private val _ticksProviders =  new RelayChannel (logger, "tick provider")
  private val _tradeConnectors = new RelayChannel (logger, "trade connector")

  private def tradeConnectorControllerFactory(balance:Fraction, equity:Fraction) =
    new RelayChannel.ControllerFactory {
      override def create(logger:Logger,
                          client:ConnectionHandle,
                          key:String,
                          onDisconnected:()=>Unit) =
        new TradeConnectorController(tradeConnectorLogger,
                                     client,
                                     key,
                                     onDisconnected,
                                     balance,
                                     equity)
    }

  private def onNewConnection(client:ConnectionHandle):Unit = {
    val clientId = HubService.nextClientId

    logger.log("client ", clientId, " connected")

    def dropClient() = {
      logger.log("client ", clientId, " disconnected")

      client.close()
    }

    def receiveIntroductionPacket(packet:Array[Byte]) = {
      HubProtocol.readPacket(packet) match {
        case HubProtocol.RegisterTicksProvider(key) => {
          _ticksProviders.putConnector(clientId,
                                       key,
                                       client,
                                       _tickProviderControllerFactory)
        }

        case HubProtocol.AskTicksProvider(key) => {
          _ticksProviders.putClient(clientId, key, client)
        }

        case HubProtocol.RegisterTradeConnector(key, balance, equity) => {
          _tradeConnectors.putConnector(clientId,
                                        key,
                                        client,
                                        tradeConnectorControllerFactory(balance, equity))
        }

        case HubProtocol.AskTradeConnector(key) => {
          _tradeConnectors.putClient(clientId, key, client) 
        }

        case unexpectedPacket => {
          logger.log("client ", clientId, " sent unexpected packet: " + unexpectedPacket)

          client.close()
        }
      }
    }


    client.setHandlers(onPacket = receiveIntroductionPacket _,
                       onDisconnect = dropClient _)
  }

  def close() = serviceConnection.close()

}
