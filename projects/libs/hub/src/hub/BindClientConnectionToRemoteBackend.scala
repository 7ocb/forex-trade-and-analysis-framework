package tas.hub

import tas.service.{
  ConnectionHandle,
  Address
}

import tas.trading.{
  TradeExecutor,
  TradeBackend,
  AccountInformationKeeper
}

import scala.collection.mutable.{
  ListBuffer,
  Map
}

import tas.output.logger.Logger
import tas.hub.clients.RemoteTradeBackend

class BindClientConnectionToRemoteBackend(logger:Logger,
                                          remoteTradeClient:ConnectionHandle,
                                          backend:RemoteTradeBackend,
                                          onLostClient:BindClientConnectionToRemoteBackend=>Unit) {

  import scala.language.implicitConversions

  case class ExecutorHandle(val executor:TradeExecutor) {
    private var isClosed = false

    def closeExecutor() = {
      if ( ! isClosed) {
        isClosed = true
        executor.closeTrade()
      }
    }
  }

  implicit def executorHandle2Executor(handle:ExecutorHandle):TradeExecutor = handle.executor

  import HubProtocol._

  private var isClosed = false
  
  remoteTradeClient.setHandlers(onPacket = onPacket _,
                                onDisconnect = onDisconnected _)

  backend.balanceMayBeChanged += (() => {
                                    send(new CurrentBalance(backend.balance))
                                  } )

  backend.equityMayBeChanged += (() => send(new CurrentEquity(backend.equity)))

  private val _executors = Map[Long, ExecutorHandle]()

  private def onPacket(buffer:Array[Byte]):Unit = {
    readPacket(buffer) match {
      case RequestNewId() => {
        send(new NewId(allocateNewId()))
      }

      case OpenTrade(id, request, stopValue, takeProfit) => {

        def onMessage(message:String) = {
          send(new MessageAboutTrade(id, message))
        }

        def onFreed() = {
          _executors.remove(id)
          send(new FreeTrade(id))
        }

        val newExecutor = new ExecutorHandle(backend.newTradeExecutor(request,
                                                                      onMessage,
                                                                      onFreed))

        _executors.put(id, newExecutor)

        def onOpened:Unit = send(new OpenedResponse(id))
        def onExternallyClosed:Unit = {
          send(new ExternallyClosed(id))
        }
        
        newExecutor.openTrade(stopValue,
                              takeProfit,
                              onOpened _,
                              onExternallyClosed _)
      }

      case CloseRequest(id) => forExecutor(id, _.closeExecutor())

      case UpdateStopRequest(id, stopValue) => forExecutor(id, _.setStop(stopValue));

      case UpdateTakeProfitRequest(id, takeProfit) => forExecutor(id, _.setTakeProfit(takeProfit));

      case unexpectedPacket => {
        logger.log("unexpected packet: ", unexpectedPacket)
      }
    }
  }

  private var _nextId:Long = 0
  
  private def allocateNewId():Long = {
    _nextId += 1
    _nextId
  }

  private def send(packet:Packet) = if ( !isClosed ) remoteTradeClient.sendRawData(writePacket(packet))

  private def onDisconnected() = {
    close()
    onLostClient(this)
  }

  def close() {
    isClosed = true
    _executors.values.foreach(_.closeExecutor())
    _executors.clear()
    remoteTradeClient.close()
  }

  private def forExecutor(id:Long, action:(ExecutorHandle)=>Unit) = {
    val executor = _executors.get(id)
    if (executor.isDefined) {
      action(executor.get)
    }
  }

}

