package tests.utils

import org.scalatest.FlatSpec

class ProtocolTests[PacketBaseClass] extends FlatSpec {

  def createTester(write:PacketBaseClass=>Array[Byte],
                   read:Array[Byte]=>PacketBaseClass) = {

    (packets:List[PacketBaseClass]) => {
      it should ("correctly process " + packets(0).getClass.getSimpleName) in {
        packets.foreach( packet => {
                          val buffer = write(packet)
                          val output = read(buffer)
                          assert(packet === output)
                        })
      }
    }

  }

}
