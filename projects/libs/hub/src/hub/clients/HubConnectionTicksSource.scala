package tas.hub.clients

import java.io.IOException

import tas.hub.HubProtocol

import tas.output.logger.Logger

import tas.events.Event

import tas.service.Address
import tas.service.{
  ConnectionHandle,
  SocketConnectionHandle
}

import tas.types.{
  Fraction,
  Interval,
  Price
}

import tas.sources.ticks.{
  TickSource
}

import tas.concurrency.RunLoop

object HubConnectionTicksSource {
  val RetryInterval = Interval.seconds(5)
}

class HubConnectionTicksSource(runLoop:RunLoop,
                               logger:Logger,
                               hubAddress:Address,
                               key:String) extends TickSource {

  private val _event = Event.newSync[Price]

  private var _client:ConnectionHandle = null

  startConnection()

  private def startConnection():Unit = {
    try {
      _client = SocketConnectionHandle.connect(runLoop,
                                               hubAddress)

      _client.setHandlers(onPacket = onPacketReceived,
                          onDisconnect = () => throw new RuntimeException("Lost connection to hub!"))

      _client.sendRawData(HubProtocol.writePacket(new HubProtocol.AskTicksProvider(key)))
    } catch {
      case ioe:IOException => retryConnectionLater()
    }
  }

  private def retryConnectionLater() {
    runLoop.postDelayed(HubConnectionTicksSource.RetryInterval,
                        startConnection _)
  }


  private def onPacketReceived(packet:Array[Byte]) = {
    HubProtocol.readPacket(packet) match {
      case HubProtocol.OnTick(bid, ack) => _event << new Price(bid, ack)
      case HubProtocol.ResourceNotFound() => {
        _client.close()
        _client = null

        retryConnectionLater()
      }

      case HubProtocol.TickProviderConnected() => {
        logger.log("Connected to hub ticks source")
      }

      case wrongPacket => {
        logger.log("Hub sent wrong packet: ", wrongPacket)
      }
    }
  }

  def tickEvent:Event[Price] = _event

  override def toString:String = "hub connection ticks for key: " + key
}
