package tests.probing.types


import tests.utils.ProtocolTests

import java.io.File

object ProbingProtocolTests {
  case class Config(val a:Int, val b:String) extends Serializable
}

import tas.probing.running.Protocol
import tas.probing.running.Protocol._

class ProbingProtocolTests extends ProtocolTests[Packet] {

  import ProbingProtocolTests.Config

  val testPacket = createTester(Protocol.writePacket _,
                                Protocol.readPacket _)
  
  testPacket(List(new Terminate()))
  testPacket(List(new RunTask("logger path",
                              List("a",
                                   "b"),
                              null),

                  new RunTask("logger path",
                              List("c",
                                   "d",
                                   "e"),
                              new Config(1,
                                         "2"))))
  testPacket(List(new TaskCompleted()))
  
}   
