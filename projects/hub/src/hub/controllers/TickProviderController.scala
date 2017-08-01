package tas.hub.controllers

import scala.collection.mutable.ListBuffer

import tas.service.ConnectionHandle

import tas.hub.{
  HubProtocol,
  RelayChannel
}

import tas.output.logger.Logger



class TickProviderController(logger:Logger,
                             client:ConnectionHandle,
                             providerKey:String,
                             onTickproviderDisconnected:()=>Unit) extends RelayChannel.Controller {

  private val _tickListeners = new ListBuffer[ConnectionHandle]

  client.setHandlers(onPacket = onIncomingPacket _,
                     onDisconnect = dropThisProvider _)

  private def onIncomingPacket(packet:Array[Byte]) = {
    HubProtocol.readPacket(packet) match {
      case _:HubProtocol.OnTick => {

        _tickListeners.foreach(_.sendRawData(packet))
      }

      case wrongPacket => {
        // ticks provider can't send anything except of tick
        logger.log("Tick provider \"" + providerKey + "\" sent wrong packet: " + wrongPacket)

        dropThisProvider() 
      }
    }
  }

  def addClient(tickListener:ConnectionHandle) = {

    tickListener.sendRawData(HubProtocol.writePacket(new HubProtocol.TickProviderConnected()))

    _tickListeners += tickListener

    logger.log("Tick provider \"" + providerKey + "\" got new listener")

    def dropTickListener() = {
      logger.log("Tick provider \"" + providerKey + "\" lost listener")
      tickListener.close()
      _tickListeners -= tickListener
    }

    def onTickListenerIncomingPacket(packet:Array[Byte]) = {
      // nothing can be sent from the ticks listener
      dropTickListener()
    }

    tickListener.setHandlers(onPacket = onTickListenerIncomingPacket _,
                             onDisconnect = dropTickListener _)

  }

  private def dropThisProvider() = {
    logger.log("Tick provider \"" + providerKey + "\" disconnected")

    _tickListeners.foreach(_.close())

    client.close()
    onTickproviderDisconnected()
  }
}
