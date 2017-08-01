package testing

import tas.events.Event

import org.scalatest.FlatSpec
import tas.ParameterSet
import tas.testing.AsyncCallOrderTrace

import org.scalamock.scalatest.MockFactory

import tas.testing.TimerSupport
import tas.timers.Timer

class ParameterSetTest extends FlatSpec with MockFactory with TimerSupport {

  def commonTestCode(createParamSet: Timer=>ParameterSet) = {

    it should "recalc when binding changed" in timerRun {
      val paramSet = createParamSet(timer)
      val event = Event.newSync[Int]

      val testValue = 14
  
      val value = paramSet.binding(event)

      val expectedToBeCalled = mock[()=>Unit]
      (expectedToBeCalled.apply _).expects()
      
      paramSet.onUpdate {
        expectedToBeCalled()
        assert(testValue === value.value)
      }

      event << 14
    }

    it should "recalc when created value changed" in timerRun {
      val paramSet = createParamSet(timer)

      val testValue = 14
      
      val value = paramSet.create[Int]

      val expectedToBeCalled = mock[()=>Unit]
      (expectedToBeCalled.apply _).expects()
      
      paramSet.onUpdate {
        expectedToBeCalled()
        assert(testValue === value.value)
      }

      value << 14
    } 
    
    it should "not dispatch onUpdate if not all parameters set" in timerRun {
      val paramSet = createParamSet(timer)

      val parameters = List(paramSet.create[Int],
                            paramSet.create[Int],
                            paramSet.create[Int])

      paramSet.onUpdate {
        throw new Error("Should not be called")
      } 

      parameters(0) << 14
      parameters(2) << 14
    }

    it should "execute only one posted onUpdate even if several updates" in timerRun {
      val paramSet = createParamSet(timer)

      val expectedToBeCalled = mock[Int=>Unit]
      (expectedToBeCalled.apply _).expects(20)

      val value = paramSet.create[Int]
      
      paramSet.onUpdate { expectedToBeCalled(value.value) }

      value << 10

      value << 20
      value << 30
      value << 20
    } 
    
  } 
  
  behavior of "always recalc parameter set"

  commonTestCode(ParameterSet.newAlwaysRecalc(_))


  it should "recalculate every time value set" in timerRun {
    val paramSet = ParameterSet.newAlwaysRecalc(timer)

    val value = paramSet.create[Int]
    
    val expectedToBeCalled = mock[Int=>Unit]
    (expectedToBeCalled.apply _).expects(10).onCall((i:Int) => value << 10 )
    (expectedToBeCalled.apply _).expects(10).onCall((i:Int) => value << 20 )
    (expectedToBeCalled.apply _).expects(20).onCall((i:Int) => value << 20 )
    (expectedToBeCalled.apply _).expects(20)
    
    paramSet.onUpdate { expectedToBeCalled(value.value) }

    value << 10
  } 
  
  behavior of "on change recalc parameter set"

  commonTestCode(ParameterSet.newRecalcOnChange(_))


  it should "not recalculate if value is set but not changed" in timerRun {
    val paramSet = ParameterSet.newRecalcOnChange(timer)

    val value = paramSet.create[Int]
    
    val expectedToBeCalled = mock[Int=>Unit]
    (expectedToBeCalled.apply _).expects(10).onCall((i:Int) => value << 10 )
    
    paramSet.onUpdate { expectedToBeCalled(value.value) }

    value << 10
  }
} 
