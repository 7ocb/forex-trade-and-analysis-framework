
import tas.concurrency.{
  NewThreadWorker,
  RunLoop
}

import tas.trading.{
  TradeType,
  TradeRequest,
  TradeValue,
  TradeMargin,
  TradeExecutor,
  Sell,
  Buy
}

import tas.trading.simulation.TradingSimulation
import tas.trading.simulation.TradingSimulation.Configuration

import tas.hub.clients.HubConnectionTradeBackend
import tas.hub.clients.HubConnectionTicksSource

import tas.output.logger.ScreenLogger
import tas.service.AddressByName

import tas.trading.Boundary

import tas.types.{
  Fraction
}

import tas.timers.RealTimeTimer
import tas.output.logger.PrefixTimerTime
import tas.output.logger.FileLogger

val threadWorker = new NewThreadWorker()

val runLoop = new RunLoop()


val hubAddress = new AddressByName("127.0.0.1", 9101)

val timer = new RealTimeTimer(runLoop)

// val baseLogger = ScreenLogger
val baseLogger = new FileLogger("tester.log",
                                FileLogger.PlainReopening)

val logger = new PrefixTimerTime(timer,
                                 baseLogger)


val hubKey = "eurusd-mt-finam-limited"

val tickSource =
  new HubConnectionTicksSource(runLoop,
                               logger,
                               hubAddress,
                               hubKey)


var currentPrice:Fraction = 0

tickSource.tickEvent += (tick => {
    val price = tick.price
    logger.log("got price: " + price)
    currentPrice = price
  })

val hubTrading =
  new HubConnectionTradeBackend(runLoop,
                                logger,
                                hubAddress,
                                hubKey)

threadWorker.run(() => runLoop())


val tradingSimulator =
  new TradingSimulation(new TradingSimulation.Configuration(TradingSimulation.Pessimistic,
                                                            "0.0005",
                                                            "0.0005",
                                                            "0.0005",
                                                            "0.0005"),
                        timer,
                        200,
                        5000,
                        logger,
                        "0.0003",
                        tickSource.tickEvent)




def run(action: =>Unit) {
  runLoop.post(() => action)
}


trait ComboTradeExecutor {

  def openTrade(stopValue:Boundary,
                takeProfitValue:Option[Boundary]):Unit

  def closeTrade:Unit

  def setStop(stopValue:Boundary):Unit

  def setTakeProfit(takeProfitValue:Option[Boundary]):Unit
}

def createExecutor(request:TradeRequest) = {
  var hubExecutor:TradeExecutor = null
  var simulationExecutor:TradeExecutor = null

  run {
    hubExecutor        = hubTrading.newTradeExecutor(request)
    simulationExecutor = tradingSimulator.newTradeExecutor(request)
  }

  new ComboTradeExecutor {

    def openTrade(stopValue:Boundary,
                  takeProfitValue:Option[Boundary]):Unit = {
      run {
        hubExecutor.openTrade(stopValue,
                              takeProfitValue,
                              () => logger.log("hub trade opened"),
                              () => logger.log("hub trade externally closed"))
        simulationExecutor.openTrade(stopValue,
                                     takeProfitValue,
                                     () => logger.log("simulation trade opened"),
                                     () => logger.log("simulation trade externally closed"))
      }
    }

    def closeTrade:Unit = {
      run {
        hubExecutor.closeTrade
        simulationExecutor.closeTrade
      }
    }

    def setStop(stopValue:Boundary):Unit = {
      run {
        hubExecutor.setStop(stopValue)
        simulationExecutor.setStop(stopValue)
      }
    }

    def setTakeProfit(takeProfitValue:Option[Boundary]):Unit = {
      run {
        hubExecutor.setTakeProfit(takeProfitValue)
        simulationExecutor.setTakeProfit(takeProfitValue)
      }
    }
  }
}

def openTrade(tradeType:TradeType,
              value:Fraction,
              stopOffset:Fraction,
              delayOffset:Fraction = null,
              takeOffset:Fraction = null) = {
  
  val (delay,
       expectedOpenPrice) = if (delayOffset != null) (Some(tradeType.delay(currentPrice,
                                                                          delayOffset)),
                                                      (currentPrice - (tradeType * delayOffset)))
                            else (None, currentPrice)

  val take = if (takeOffset != null) Some(tradeType.takeProfit(expectedOpenPrice,
                                                               takeOffset))
             else None

  val executor = createExecutor(new TradeRequest(value,
                                                 tradeType,
                                                 delay))

  val stop = tradeType.stop(expectedOpenPrice,
                                    stopOffset)

  println("stop: " + stop)
  println("take: " + take)
  println("delay: " + delay)

  executor.openTrade(stop,
                     take)

  executor
}

// def openTrade(request:TradeRequest, )

// def openTrade()


// var executor:TradeExecutor = null

// def createExecutor() = {
//   executor = trading.newTradeExecutor(new TradeRequest("1000",
//                                                        Sell,
//                                                        None))
// }

// def openTrade() = {
//   executor.openTrade(Boundary <= "5.2",
//                      None,
//                      () => logger.log("opened"),
//                      () => logger.log("externally closed"))
// }

// def closeTrade() = {
//   executor.closeTrade
// }


// def testTrade() {
//   run {
//     executor = trading.newTradeExecutor(new TradeRequest("2000",
//                                                          Buy,
//                                                          Some(Boundary >= "1.35840")))

//     executor.openTrade(Boundary <= "1.36000",
//                        None,
//                        () => logger.log("opened"),
//                        () => logger.log("externally closed"))
//   }
// }


// testTrade()
