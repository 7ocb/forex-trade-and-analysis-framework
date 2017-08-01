
import tas.types.Interval
import tas.timers.Timer
import tas.timers.JustNowFakeTimer
import tas.ActiveValue

import scala.collection.mutable.ListBuffer
import tas.ParameterSet

import tas.trading.TradeExecutor
import tas.trading.TradeHandle

import tas.trading.KeepTradeOpened
import tas.trading.CloseTrade
import tas.trading.CloseCondition
import tas.trading.Boundary

import tas.Bound
import tas.NotBound

import tas.testing.AsyncCallOrderTrace



object TestMain extends App with AsyncCallOrderTrace {
  

  def conditionsSwitchs(endCondition:ActiveValue[CloseCondition], startingFrom:Int) {
    Timer.run {
      calledInOrder(startingFrom)

      endCondition << KeepTradeOpened
    }

    Timer.run {
      calledInOrder(startingFrom + 1)

      endCondition << CloseTrade
    }
    
    Timer.run {
      calledInOrder(startingFrom + 2)

      endCondition << KeepTradeOpened
    }

    Timer.run {
      calledInOrder(startingFrom + 3)

      endCondition << CloseTrade
    }
  }

  val stopValue = new ActiveValue[Boundary](Boundary < 2) with NotBound
  

  asyncTest {
    // this test for condition when we startin async trade opening request
    // and then closing it when condition evaluates to CloseTrade
    
    val endCondition = new ActiveValue[tas.trading.CloseCondition] with NotBound

    val tradeExecutor = new TradeExecutor with NotBound {
      override def openTrade(stopValue:Boundary, onOpened:()=>Unit, onExternallyClosed:()=>Unit) = {
        calledInOrder(0)
        
        onOpened()
      } 

      override def closeTrade = calledInOrder(6)
      override def setStop(stopValue:Boundary) = shouldNotBeCalled
    }

    val handle = new TradeHandle(endCondition, stopValue, tradeExecutor)

    conditionsSwitchs(endCondition, 2)
    
    calledInOrder(1)
  }

  asyncTest {

    // this test checks case for fully synchronous trade operations
    val endCondition = new ActiveValue[tas.trading.CloseCondition] with Bound {
      override def unbindAll = calledInOrder(6)
    } 

    val tradeExecutor = new TradeExecutor with NotBound {
      override def openTrade(stopValue:Boundary, onOpened:()=>Unit, onExternallyClosed:()=>Unit) = {
        calledInOrder(0)

        onOpened()
      } 

      override def closeTrade = calledInOrder(7)

      override def setStop(stopValue:Boundary) = shouldNotBeCalled
    }

    val handle = new TradeHandle(endCondition, stopValue, tradeExecutor)

    conditionsSwitchs(endCondition, 2)
    
    calledInOrder(1)
  } 

  // test request completion

  asyncTest {

    // this test checks case for fully synchronous trade operations
    val endCondition = new ActiveValue[tas.trading.CloseCondition] with Bound {
      override def unbindAll = calledInOrder(7)
    } 

    val tradeExecutor = new TradeExecutor with NotBound {
      override def openTrade(stopValue:Boundary, onOpened:()=>Unit, onExternallyClosed:()=>Unit) = {

        calledInOrder(0)
        
        Timer.run {
          calledInOrder(2)
          onOpened()
        } 
      } 

      override def closeTrade = calledInOrder(8)

      override def setStop(stopValue:Boundary) = shouldNotBeCalled
    }

    val handle = new TradeHandle(endCondition, stopValue, tradeExecutor)

    conditionsSwitchs(endCondition, 3)
    
    calledInOrder(1)
  }


  // test resetting stop value
  asyncTest {

    val firstNewStopValue:Boundary = Boundary < 20
    val secondNewStopValue = Boundary < 23
    val stopValue = new ActiveValue[Boundary](firstNewStopValue) with NotBound

    
    // this test checks case for fully synchronous trade operations
    val endCondition = new ActiveValue[tas.trading.CloseCondition] with Bound {
      override def unbindAll = calledInOrder(9)
    } 

    val tradeExecutor = new TradeExecutor with NotBound {
      override def openTrade(stopValue:Boundary, onOpened:()=>Unit, onExternallyClosed:()=>Unit) = {

        if (stopValue != firstNewStopValue) throw new Error("stop values differ!")
        
        calledInOrder(0)
        
        Timer.run {
          calledInOrder(2)
          onOpened()
        } 
      } 

      override def closeTrade = calledInOrder(10)

      override def setStop(stopValue:Boundary) = {
        calledInOrder(8)
        if (stopValue != secondNewStopValue) throw new Error("stop values differ: " + stopValue + " != " + secondNewStopValue)
      } 
    }

    val handle = new TradeHandle(endCondition, stopValue, tradeExecutor)

    Timer.run {
      stopValue << secondNewStopValue
      calledInOrder(3)
    } 
    
    conditionsSwitchs(endCondition, 4)
    
    calledInOrder(1)
  }
  
}   
