package testing.utils

import org.scalamock.scalatest.MockFactory

trait Checkpoints {
  this:MockFactory =>

  class Checkpoint extends (()=>Unit) {
    private val slave = mock[() => Unit]
    def expect = (slave.apply _).expects()
    def apply() = slave()
  } 

  def newCheckpoint = new Checkpoint
} 
