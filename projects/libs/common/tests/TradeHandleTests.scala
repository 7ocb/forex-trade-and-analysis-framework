package testing.trading

import tas.timers.Timer

import tas.events.Event

import org.scalatest.FlatSpec
import org.scalatest.OneInstancePerTest
import org.scalamock.scalatest.MockFactory

import tas.testing.TimerSupport
import testing.utils.Checkpoints

import tas.trading.CloseCondition
import tas.trading.KeepTradeOpened
import tas.trading.CloseTrade

import tas.types.Boundary

import tas.trading.TradeExecutor
import tas.trading.TradeHandle
import tas.ActiveValue

import tas.timers.Timer

import tas.NotBound

class TradeHandleTest extends FlatSpec with MockFactory with TimerSupport with Checkpoints {
  behavior of "TradeHandle"

  trait UnboundExecutor extends TradeExecutor with NotBound

  class TestUtils(timer:Timer) {
    val initialStop = Boundary < 2
    
    val stop = new ActiveValue(timer, initialStop) with NotBound
    val takeProfit = new ActiveValue[Option[Boundary]](timer) with NotBound
    val end = new ActiveValue[CloseCondition](timer) with NotBound

    val executor = mock[UnboundExecutor]
    
    def spawnHandle = new TradeHandle(end,
                                      stop,
                                      takeProfit,
                                      executor)

    def expectClose = {
      (executor.closeTrade _).expects()
                                     (executor.unbindAll _).expects()
    }
  }


  def closeTest(comment:String,
                closeWay:(TradeHandle, ActiveValue[CloseCondition])=>Unit) {
    it should comment in timerRun (
      new TestUtils(timer) {

        val checkBeforeStart = newCheckpoint
        val checkBeforeSwitch = newCheckpoint
        val checkAfterClose = newCheckpoint

        inSequence {

          (executor.openTrade _).expects(initialStop,
                                         None,
                                         *,
                                         *).onCall((_, _, onOpened, _) => {
                                                     onOpened()
                                                   })
          
          checkBeforeStart.expect
          checkBeforeSwitch.expect

          expectClose

          checkAfterClose.expect
        }
        
        val handle = spawnHandle

        checkBeforeStart()
        
        timer.run {

          assert(handle.isBecameOpened === true)
          
          checkBeforeSwitch()
          closeWay(handle, end)

          timer.run {
            checkAfterClose()

            assert(handle.isBecameClosed === true)

            // this switch must not lead to any calls
            end << KeepTradeOpened
            end << CloseTrade

            timer.run {
              handle.close()
            }
          }
        }
      })
  }

  closeTest("close trade when condition evaluates to CloseTrade",
            (_, end) => end << CloseTrade)
  
  closeTest("close trade when close called",
            (handle, _) => handle.close)

  it should "not call executor's close if externally closed called" in timerRun (
    new TestUtils(timer) {

      val checkBeforeStart = newCheckpoint
      val checkBeforeSwitch = newCheckpoint
      val checkAfterClose = newCheckpoint

      var externallyClosed:()=>Unit = null

      val mockedObserver = mock[TradeHandle.Observer]

      
      inSequence {

        (executor.openTrade _).expects(initialStop,
                                       None,
                                       *,
                                       *).onCall((_, _, onOpened, extClosed) => {
                                                   externallyClosed = extClosed
                                                   onOpened()
                                                 })
        
        checkBeforeStart.expect
        checkBeforeSwitch.expect

        (executor.unbindAll _).expects()

        (mockedObserver.onExternallyClosed _).expects()

        checkAfterClose.expect
      }
      
      val handle = spawnHandle
      handle.setObserver(mockedObserver)

      checkBeforeStart()
      
      timer.run {
        checkBeforeSwitch()

        externallyClosed()

        timer.run {
          checkAfterClose()

          // this switch must not lead to any calls
          end << KeepTradeOpened
          end << CloseTrade

          timer.run {
            handle.close()
          }
        }
      }
    })

  it should "not became opened if onOpened not called" in timerRun (
    new TestUtils(timer) {

      val checkBeforeStart = newCheckpoint
      val checkBeforeSwitch = newCheckpoint
      val checkAfterClose = newCheckpoint

      var externallyClosed:()=>Unit = null
      
      inSequence {

        (executor.openTrade _).expects(initialStop,
                                       None,
                                       *,
                                       *).onCall((_, _, onOpened, extClosed) => {
                                                   timer.run {
                                                     onOpened()
                                                   }
                                                 })
      }
      
      val handle = spawnHandle

      assert(handle.isBecameOpened === false)
      assert(handle.isBecameClosed === false)

    })

  def setterTest(comment:String,
                 expectation: TradeExecutor=>Unit,
                 setting: (TestUtils)=>Unit) = {
    it should comment in timerRun (
      new TestUtils(timer) {

        val checkBeforeStart = newCheckpoint
        val checkBeforeSwitch = newCheckpoint
        val checkAfterChange = newCheckpoint

        inSequence {

          (executor.openTrade _).expects(initialStop,
                                         None,
                                         *,
                                         *).onCall((_, _, onOpened, _) => {
                                                     onOpened()
                                                   })
          
          checkBeforeStart.expect
          checkBeforeSwitch.expect

          expectation(executor)
          
          checkAfterChange.expect

          expectClose
        }
        
        spawnHandle

        checkBeforeStart()
        
        timer.run {
          checkBeforeSwitch()
          setting(this)

          timer.run {
            checkAfterChange()

            end << CloseTrade
          }
        }
      })
  }

  setterTest("call setStop if stop changed",
             executor => (executor.setStop _).expects(Boundary < 3),
             _.stop << (Boundary < 3))

  setterTest("call setTakeProfit if take profit changed",
             executor => (executor.setTakeProfit _).expects(Some(Boundary < 3)),
             _.takeProfit << Some(Boundary < 3))

  
  it should "call unbindAll on all parameters on close" in timerRun (
    new TestUtils(timer) {

      class BoundActiveValue[T] extends ActiveValue[T](timer) with NotBound
      
      val mockedStop = mock[BoundActiveValue[Boundary]]
      val mockedEnd = mock[BoundActiveValue[CloseCondition]]
      val mockedTakeProfit = mock[BoundActiveValue[Option[Boundary]]]
      

      (executor.openTrade _).expects(initialStop,
                                     None,
                                     *,
                                     *).onCall((_, _, onOpened, _) => {
                                                 onOpened()
                                               })

      val newStop = Event.newAsync[Boundary](timer)
      val newTakeProfit = Event.newAsync[Option[Boundary]](timer)
      val newEnd = Event.newAsync[CloseCondition](timer)
      
      (mockedStop.onValueChanged _).expects().returning(newStop)
                                                       (mockedStop.onValueChanged _).expects().returning(newStop)
                                                                                                        (mockedStop.unbindAll _).expects()
                                                                                                                                        (mockedStop.value _).expects().returning(initialStop)

      (mockedTakeProfit.onValueChanged _).expects().returning(newTakeProfit)
                                                             (mockedTakeProfit.onValueChanged _).expects().returning(newTakeProfit)
                                                                                                                    (mockedTakeProfit.isValue _).expects().returning(false)
                                                                                                                                                                    (mockedTakeProfit.unbindAll _).expects()

      (mockedEnd.onValueChanged _).expects().returning(newEnd)
                                                      (mockedEnd.onValueChanged _).expects().returning(newEnd)
                                                                                                      (mockedEnd.unbindAll _).expects()
                                                                                                                                     (mockedEnd.isValue _).expects()
      
      expectClose
      
      new TradeHandle(mockedEnd, mockedStop, mockedTakeProfit, executor)

      
      timer.run {
        newEnd << CloseTrade
      }
    })

  it should "throw an error if created on condition == CloseTrade" in timerRun (
    new TestUtils(timer) {
      end << CloseTrade
      intercept[Error] {
        spawnHandle
      }
    })
  
}
