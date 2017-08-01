package tas.hub

import tas.service.ConnectionHandle
import tas.output.logger.Logger

import scala.collection.mutable.Map

object RelayChannel {

  trait Controller {
    def addClient(client:ConnectionHandle)
  }

  trait ControllerFactory {
    def create(logger:Logger,
               client:ConnectionHandle,
               key:String,
               onDisconnected:()=>Unit):Controller
  }
}

class RelayChannel(_logger:Logger, _name:String) {

  import RelayChannel._

  private val _controllers = Map[String, Controller]()

  def putConnector(clientId:Long,
                   key:String,
                   client:ConnectionHandle,
                   controllerFactory:RelayChannel.ControllerFactory) = {
    import HubProtocol._

    if (_controllers.get(key) != None) {
      _logger.log("client ", clientId, " tried REregister as " + _name + ": \"", key, "\"")

      client.sendRawData(writePacket(new AlreadyRegistered()))

      client.close()
    } else {
      _logger.log("client ", clientId, " registered as " + _name + ": \"", key, "\"")

      client.sendRawData(writePacket(new Registered()))

      def onDisconnected() = {
        _logger.log("client ", clientId, " (", _name, ")", " disconnected")
        _controllers.remove(key)
      }

      _controllers += ((key,
                        controllerFactory.create(_logger,
                                                 client,
                                                 key,
                                                 onDisconnected _)))
    }
  }

  def putClient(clientId:Long, key:String, client:ConnectionHandle) = {
    import HubProtocol._

    val controller = _controllers.get(key)

    if (controller.isEmpty) {
      _logger.log("client ", clientId, " failed to obtain " + _name + " \"", key, "\"" )

      client.sendRawData(writePacket(new ResourceNotFound()))

      client.close()
    } else {
      _logger.log("client ", clientId, " connected to " + _name + " \"", key, "\"" )

      controller.get.addClient(client)
    }
  }
}

