package tas.hub.clients.connected

import tas.hub.clients.RemoteTradeBackend

import tas.hub.HubProtocol

import tas.types.Fraction

import tas.service.{
  ConnectionHandle
}

import tas.output.logger.Logger

import scala.collection.mutable.ListBuffer

import tas.{
  Bound
}

import tas.trading.{
  TradeRequest,
  TradeExecutor
}

import tas.output.warning.Warning

class ConnectedRemoteTradeBackend(connection:ConnectionHandle,
                                  logger:Logger,
                                  private var _balance:Fraction,
                                  private var _equity:Fraction,
                                  handleDisconnect:()=>Unit) extends RemoteTradeBackend {

  import HubProtocol._

  private var _executors = new ListBuffer[Executor]
  private var _disconnected = false

  connection.setHandlers(onPacket = handleRawPacket _,
                         onDisconnect = onDisconnect)

  private def onDisconnect():Unit = {
    connection.close()
    _disconnected = true
    handleDisconnect()
  }

  final def newTradeExecutor(request:TradeRequest,
                             onMessage:(String)=>Unit,
                             onFreed:()=>Unit):(TradeExecutor with Bound) = {

    val newExecutor = new Executor(request,
                                   onMessage,
                                   onFreed,
                                   sendPacket _)
    _executors += newExecutor
    newExecutor
  }

  private def sendPacket(packet:Packet) = {
    if ( ! _disconnected) {
      connection.sendRawData(writePacket(packet))
    }
  }

  private def handleRawPacket(buffer:Array[Byte]) = {
    val packet = readPacket(buffer)

    packet match {
      case OpenedResponse(id) => forExecutorWithId(id, _.opened)
      case ExternallyClosed(id) => forExecutorWithId(id, _.externallyClosed)

      case NewId(id) => forExecutorWithoutId(_.connected(id))
      case MessageAboutTrade(id, message) => forExecutorWithId(id, _.messageHandler(message))
      case FreeTrade(id) => freeExecutorWithId(id)

      case CurrentBalance(newBalance) => {
        _balance = newBalance
        _balanceMayBeChanged()
      }

      case CurrentEquity(newEquity) => {
        _equity = newEquity
        _equityMayBeChanged()
      }

      case unexpectedPacket => {
        logger.log("unexpected packet received", unexpectedPacket)
        connection.close()
      }
    }

  }

  def balance:Fraction = _balance
  def equity:Fraction = _equity

  private def freeExecutorWithId(id:Long) = {
    forExecutorWithId(id, _.onFreed())
    _executors = _executors.filterNot(_.id == id)
  }

  private def forExecutorWithId(id:Long, action:Executor=>Unit) = {
    _executors.find(_.id == id) match {
      case Some(executor) => action(executor)
      case None => Warning("received a packet for missing executor ", id)
    }
  }

  private def forExecutorWithoutId(action:Executor=>Unit) = {
    forExecutorWithId(InvalidId, action)
  }

  final def close() = {
    connection.close()
  }
}
