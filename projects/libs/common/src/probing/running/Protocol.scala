package tas.probing.running

import tas.types.{
  Trade, Sell, Buy,
  Boundary
}

import tas.trading.TradeValueType
import tas.trading.TradeMargin
import tas.trading.TradeValue

import tas.trading.TradeRequest

import java.io.InputStream
import java.io.ObjectInputStream
import java.io.ByteArrayInputStream
import java.io.ObjectOutputStream
import java.io.ByteArrayOutputStream

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
  import tas.utils.IO.withStream

  sealed trait Packet
  
  sealed trait ClientToServerPacket extends Packet
  sealed trait ServerToClientPacket extends Packet

  case class RunTask(val logPath:String, val prefixPostfixLines:List[String], val config:Serializable) extends ServerToClientPacket
  case class Terminate() extends ServerToClientPacket

  case class TaskCompleted() extends ClientToServerPacket

  def writePacket(packet:Packet):Array[Byte] = {
    val byteStream = new ByteArrayOutputStream()

    withStream(new ObjectOutputStream(byteStream),
               (stream:ObjectOutputStream) => {
                 stream.writeClassName(packet)

                 packet match {
                   case RunTask(logPath, prefixPostfix, config) => {
                     stream.writeUTF(logPath)
                     stream.writeObject(prefixPostfix)
                     stream.writeObject(config)
                   }

                   case TaskCompleted() => {}
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
                   case "RunTask" => new RunTask(stream.readUTF,
                                                 stream.readObject.asInstanceOf[List[String]],
                                                 stream.readObject.asInstanceOf[Serializable])

                   case "Terminate" => new Terminate()
                   case "TaskCompleted" => new TaskCompleted()
                 }                 
               })
  } 
  
} 
