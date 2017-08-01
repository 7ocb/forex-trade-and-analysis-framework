package tests.previousdaydirection 

import org.scalatest.FlatSpec
import org.scalamock.scalatest.MockFactory

import tas.previousdaydirection.strategy.TradeSet

import tas.{Bound}

import tas.trading.{TradeExecutor, Boundary}

import tas.testing.TimerSupport
import tas.timers.Timer

class TradeSetTests extends FlatSpec with MockFactory with TimerSupport {

  trait Executor extends TradeExecutor with Bound

  class SetTest {
    var set = new TradeSet(timer, fail())
    
    def open(doOpen:Boolean, doExternallyClose:Boolean) = {
  
      val executor = mock[Executor]
      (executor.openTrade _).expects(*, *, *, *).onCall((_, _, onOpened, onExternallyClosed) => {
        if (doOpen) {
          timer.run {
            onOpened()
          }
        }

        if (doExternallyClose) {
          timer.run {
            onExternallyClosed()
          } 
        } 
      } )

      if ( ! doExternallyClose) {
        (executor.closeTrade _).expects()
      } 
      (executor.unbindAll _).expects()
      set.open(executor)
      executor
    }
  }  
  
  it should "throw runtime exception when trying top open before calling setBoundaries" in (new SetTest {
    intercept[RuntimeException] {
      set.open(null)
    } 
  })

  it should "open executor" in timerRun (new SetTest {
    set.setBoundaries(Boundary < 1, Boundary > 2)

    val executor = mock[Executor]

    (executor.openTrade _).expects(Boundary < 1,
                                   Some(Boundary > 2),
                                   *,
                                   *)
    set.open(executor)

    assert(set.countActiveTrades === 1)
  })

  it should "open several executors and close all on closeAll" in timerRun (new SetTest {
    set.setBoundaries(Boundary < 1, Boundary > 2)

    open(false, false)
    open(false, false)
    open(false, false)

    assert(set.countActiveTrades === 3)

    set.closeAll

    assert(set.countActiveTrades === 0)
  })

  it should "open several executors and close only pending on closePending" in timerRun (new SetTest {
    set.setBoundaries(Boundary < 1, Boundary > 2)

    open(true, false)
    open(false, false)
    open(true, false)

    assert(set.countActiveTrades === 3)

    timer.run {
      set.closePending

      assert(set.countActiveTrades === 2)

      set.closeAll

      assert(set.countActiveTrades === 0)
    }
  })

  it should "call externally closed when at least one trade closed externally" in timerRun (new SetTest {

    val expectCall = mock[() => Unit]
    (expectCall.apply _).expects()
  
    set = new TradeSet(timer, expectCall())

    set.setBoundaries(Boundary < 1, Boundary > 2)

    open(true, false)
    open(true, false)
    open(true, true)

    assert(set.countActiveTrades === 3)

    timer.run {
      assert(set.countActiveTrades === 2)

      set.closeAll

      assert(set.countActiveTrades === 0)
    }
  })

  it should "call set stop when stop boundary changed" in timerRun (new SetTest {
    set.setBoundaries(Boundary < 1, Boundary > 2)

    val executor = open(true, false)
    (executor.setStop _).expects(Boundary < 3)
    set.setBoundaries(Boundary < 3, Boundary > 2)

    timer.run {
      set.closeAll
    } 
  } )

  it should "call set take profit when take profit boundary changed" in timerRun (new SetTest {
    set.setBoundaries(Boundary < 1, Boundary > 2)

    val executor = open(true, false)
    (executor.setTakeProfit _).expects(Some(Boundary < 3))
    set.setBoundaries(Boundary < 1, Boundary < 3)

    timer.run {
      set.closeAll
    } 
  } )

} 
