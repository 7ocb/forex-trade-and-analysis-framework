package tas.hub.clients

// import tas.service.{
//   ConnectionHandle,
//   Address
// }

// import tas.concurrency.RunLoop

import tas.trading.{
  AccountInformationKeeper,
  TradeRequest,
  TradeExecutor
//   Boundary
}

// import scala.collection.mutable.ListBuffer

// import tas.hub.HubProtocol

// import tas.output.warning.Warning
// import tas.output.logger.Logger

import tas.{
  Bound
}
//   NotBound
// }

// import tas.types.{
//   Fraction
// }

trait RemoteTradeBackend extends AccountInformationKeeper {
  def newTradeExecutor(request:TradeRequest,
                       onMessage:(String)=>Unit,
                       onFreed:()=>Unit):(TradeExecutor with Bound)
}
