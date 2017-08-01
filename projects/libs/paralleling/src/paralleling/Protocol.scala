package tas.paralleling

import java.io.Serializable
import java.io.InputStream
import java.io.ObjectInputStream
import java.io.ByteArrayInputStream
import java.io.ObjectOutputStream
import java.io.ByteArrayOutputStream
import tas.utils.IO

import scala.language.existentials

private object IOAddition {
  import scala.language.implicitConversions

  class InputStreamAddition(stream:ObjectInputStream) {
    def readClassName = stream.readUTF
  }

  class OutputStreamAddition(stream:ObjectOutputStream) {
    def writeClassName(o:Any) = stream.writeUTF(o.getClass.getSimpleName)
  }

  implicit def iostreamAddition(stream:ObjectInputStream):InputStreamAddition = new InputStreamAddition(stream)
  implicit def iostreamAddition(stream:ObjectOutputStream):OutputStreamAddition = new OutputStreamAddition(stream)  
}

object Protocol {
  import IOAddition.iostreamAddition
  import IO.withStream

  sealed trait Packet
  
  sealed trait ClientToServerPacket extends Packet
  sealed trait ServerToClientPacket extends Packet

  case class RunTask(action:Action[_ <: Serializable]) extends ServerToClientPacket
  case class Terminate() extends ServerToClientPacket

  case class TaskCompleted(result:Serializable) extends ClientToServerPacket

  def writePacket(packet:Packet):Array[Byte] = {
    val byteStream = new ByteArrayOutputStream()

    withStream(new ObjectOutputStream(byteStream),
               (stream:ObjectOutputStream) => {
                 stream.writeClassName(packet)

                 packet match {
                   case RunTask(action) => stream.writeObject(action)

                   case TaskCompleted(result) => stream.writeObject(result)
                   case Terminate() => {}
                   
                 }
                 stream
               })

    byteStream.toByteArray
  }
  
  def readPacket(buffer:Array[Byte]):Packet = {
    withStream(new ObjectInputStream(new ByteArrayInputStream(buffer)),
               (stream:ObjectInputStream) => {
                 stream.readUTF match {
                   case "RunTask" => new RunTask(stream.readObject.asInstanceOf[Action[_ <: Serializable]])

                   case "Terminate" => new Terminate()
                   case "TaskCompleted" => new TaskCompleted(stream.readObject.asInstanceOf[Serializable])
                 }                 
               })
  } 
  
} 
