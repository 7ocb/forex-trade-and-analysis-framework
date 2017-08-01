package testing.utils.reactions

import org.scalatest.FlatSpec
import org.scalamock.scalatest.MockFactory
import tas.timers.Timer
import tas.sources.periods.PeriodSource

import tas.trading.{
  TradeBackend,
  TradeExecutor,
  TradeRequest
}

import tas.types.{
  Period,
  Time,
  Interval,
  Fraction,
  Trade, Sell, Buy,
  Boundary,
  Price
}

import tas.output.logger.{Logger, NullLogger}
import tas.Bound

import tas.events.{
  Event,
  SyncSubscription
}

object StrategyBehaviorTests {

  trait StrategyFactory {
    def createStrategy(timer:Timer,
                       periods:PeriodSource,
                       tradeBackend:TradeBackend,
                       logger:Logger):Unit
  }
}

trait StrategyBehaviorTests extends FlatSpec with MockFactory with ReactionToEventTests {
  import StrategyBehaviorTests._




  def logger:Logger = NullLogger

  trait Test extends super.Test with StrategyFactory {

    

    trait Executor extends TradeExecutor with Bound

    private var _lastTime = Time.milliseconds(0)
    private val _stepInterval = Interval.seconds(1)
    
    trait TradeEmulator {
      def expectClosed()
      def eventClosedExternally()
      def eventOpened()
      def expectStopUpdated(stop:Boundary)
      def expectTakeProfitUpdated(takeProfit:Boundary)
      def expectBoundariesUpdated(stop:Boundary,
                                  takeProfit:Boundary)
    }

    private val _periodEvent = Event.newSync[Period]

    private val _sourceMock = mock[PeriodSource]

    (_sourceMock.periodCompleted _).expects().returning(_periodEvent)

    private val _trading = mock[TradeBackend]

    createStrategy(timer,
                   _sourceMock,
                   _trading,
                   logger)
    
    def eventPeriodEnded(open:Price, close:Price) = {
      nextEvent {
        sendPeriod(new Period(open, close, Price.ZERO, Price.ZERO, Time.milliseconds(0)))
      }
    }

    private def sendPeriod(period:Period) = {
      _periodEvent << period
    }

    private def nextEvent(action: =>Unit) = {
      nextEventAt(_lastTime) { action }
      _lastTime += _stepInterval
    }

    def expectBoundariesUpdated(trades:List[TradeEmulator], stop:Boundary, takeProfit:Boundary) = {
      trades.map(_.expectStopUpdated(stop))
      trades.map(_.expectTakeProfitUpdated(takeProfit))
    }

    def expectBalanceChecked(balance:Fraction) = {
      setupExpectation {
        (_trading.balance _).expects().returning(balance)
      }
    }

    def expectTradeRequested(trade:Trade, value:Fraction, delay:Boundary, stop:Boundary, takeProfit:Boundary):TradeEmulator = {
      val expectedMockedTradeExecutor = mock[Executor]

      var onOpened:()=>Unit = null
      var onExternallyClosed:()=>Unit = null

      logger.log("tradeRequested")
      
      setupExpectation {
        logger.log("expecting new trade")

        (_trading.newTradeExecutor _)
          .expects(new TradeRequest(value,
                                    trade,
                                    Some(delay)))
          .onCall((request:TradeRequest) => {

                    assert(request.value === value, "Wrong value!")
                    assert(request.tradeType === trade, "Wrong trade type!")
                    assert(request.delayUntil.get === delay, "Wrong delay!")
                    
                    expectedMockedTradeExecutor
                  } )
        
        (expectedMockedTradeExecutor.openTrade _)
          .expects(stop, Some(takeProfit), *, *)
          .onCall((actualStop, actualTakeProfit, opened, closed) => {

                    logger.log("stop set: " + actualStop.value)
                    assert(actualStop === stop, "Wrong stop")
                    logger.log("take profit set: " + actualTakeProfit.get.value)
                    assert(actualTakeProfit === Some(takeProfit), "Wrong take profit")
                    
                    onOpened = opened
                    onExternallyClosed = closed
                  } )
      }
      
      new TradeEmulator {

        def call(toCall:()=>Unit) = toCall()
        
        def expectClosed() = {
          setupExpectation {
            logger.log("expecting close/unbind for manually closed")
                      (expectedMockedTradeExecutor.closeTrade _).expects()
                                                                        (expectedMockedTradeExecutor.unbindAll _).expects()
          }
        }
        
        def eventClosedExternally() = {
          nextEvent {
            logger.log("closing externally")
            call(onExternallyClosed)
          }
          setupExpectation {
            logger.log("expecting unbind for exernally closed")
                      (expectedMockedTradeExecutor.unbindAll _).expects()
          }
          
        }

        def eventOpened() = {
          nextEvent {
            call(onOpened)
          }
        }

        def expectStopUpdated(stop:Boundary) = {
          setupExpectation {
            logger.log("expecting stop changed: " + stop)
                      (expectedMockedTradeExecutor.setStop _).expects(stop).onCall((actualStop:Boundary) => {
                                                                                     logger.log("setStop: " + actualStop.value)
                                                                                     assert(actualStop === stop)
                                                                                   } )
          }
        }

        def expectTakeProfitUpdated(takeProfit:Boundary) = {
          setupExpectation {
            logger.log("expecting take profit changed: " + takeProfit)
                      (expectedMockedTradeExecutor.setTakeProfit _).expects(Some(takeProfit)).onCall((actualTp:Option[Boundary]) => {
                                                                                                       logger.log("setTp: " + actualTp.get.value)
                                                                                                       assert(actualTp.get === takeProfit)
                                                                                                     } )
          }
        }
        
        def expectBoundariesUpdated(stop:Boundary, takeProfit:Boundary) = {
          setupExpectation {
            inAnyOrder {
              logger.log("expecting boundaries changed changed: stop " + stop + ", tp: " + takeProfit)
                        (expectedMockedTradeExecutor.setStop _).expects(stop).onCall((actualStop:Boundary) => {
                                                                                       logger.log("setStop: " + actualStop.value)
                                                                                       assert(actualStop === stop)
                                                                                     } )
                                                                                    (expectedMockedTradeExecutor.setTakeProfit _).expects(Some(takeProfit)).onCall((actualTp:Option[Boundary]) => {
                                                                                                                                                                     logger.log("setTp: " + actualTp.get.value)
                                                                                                                                                                     assert(actualTp.get === takeProfit)
                                                                                                                                                                   } )
            }
          }
        }
      }
    }
  }
}
