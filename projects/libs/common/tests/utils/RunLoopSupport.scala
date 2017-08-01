
package testing.utils

import tas.concurrency.RunLoop

import tas.types.Interval

object RunLoopSupport {

  class Timeout extends Exception("Test timed out")
  
  private class FailTest extends (() => Unit) {

    private var _aborted = false
    
    def apply() = {
      if (! _aborted) {
        throw new RunLoopSupport.Timeout 
      } 
    }

    def abort = _aborted = true
  }

} 

trait RunLoopSupport {

  private var _runLoop:RunLoop = null
  private var _fail:RunLoopSupport.FailTest = null
  

  protected final def runLoop = {
    if (_runLoop != null) _runLoop
    else throw new Error("runLoop can only be accessed within runLoopTest")
  }

  protected final def runLoopTestWithTimeout(timeout:Interval)(body: => Unit) = {
    _runLoop = new RunLoop

    _fail = new RunLoopSupport.FailTest
    _runLoop.postDelayed(timeout, _fail)
    
    try {
      body
      runLoop()
    } finally {
      _runLoop.terminate

      _fail.abort

      _fail = null
      _runLoop = null
    } 

  }

  protected final def complete = {
    if (_runLoop == null) throw new Error("complete can be called only within runLoopTest")

    _runLoop.terminate
  } 

  protected final def runLoopTest(body: => Unit):Unit = {
    runLoopTestWithTimeout(defaultTimeout) { body }
  }

  def defaultTimeout:Interval = Interval.seconds(1)
} 

