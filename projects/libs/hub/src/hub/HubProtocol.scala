package tas.hub

import tas.types.{
  Fraction,
  Trade,
  Sell,
  Buy,
  Boundary
}

import tas.trading.{
  TradeRequest
}

import java.io.{
  ByteArrayInputStream,
  ByteArrayOutputStream,
  DataOutputStream,
  DataInputStream
}

import tas.utils.IO

private object CPPIO {
  import scala.language.implicitConversions

  private val ENCODING = "UTF-8"

  class CPPInteractionInputStream(stream:DataInputStream) {
    def readUTF8String():String = {
      val length = stream.readInt

      val bytes = new Array[Byte](length)

      IO.readAllBuffer(stream, bytes)

      new String(bytes, ENCODING)
    }

    def readFraction() = Fraction(readUTF8String())

    def readTradeRequest():TradeRequest = {
      new TradeRequest(readFraction(),
                       readTrade(),
                       readOptionalBoundary())
    }

    def readOptionalBoundary():Option[Boundary] = readOption(readBoundary _)
    
    def readBoundary():Boundary = {
      val isEqual = stream.readBoolean()
      val isBelow = stream.readBoolean()
      val price = readFraction()

      if (isEqual) {
        if (isBelow) Boundary <= price
        else Boundary >= price
      } else {
        if (isBelow) Boundary < price
        else Boundary > price
      }
    }


    def readTrade() = {
      readUTF8String() match {
        case "Sell" => Sell
        case "Buy" => Buy
      }
    }

    def readOption[T](reader:()=>T) = {
      val present = stream.readBoolean()

      if (present) Some(reader())
      else None
    }
  }

  implicit def dataStream2CppInteractionStream(stream:DataInputStream):CPPInteractionInputStream =
      new CPPInteractionInputStream(stream)

  class CPPInteractionOutputStream(stream:DataOutputStream) {
    def writeUTF8String(string:String) = {
      val bytes = string.getBytes(ENCODING)

      val length = bytes.length

      stream.writeInt(length)
      stream.write(bytes, 0, length)
    }

    def writeFraction(fraction:Fraction) = writeUTF8String(fraction.toString)

    def writeClassName(o:AnyRef) = {
      writeUTF8String(o.getClass.getSimpleName)
    }

    def writeTrade(value:Trade) = {
      value match {
        case Sell => writeUTF8String("Sell")
        case Buy => writeUTF8String("Buy")
      }
    }
    
    def writeTradeRequest(tradeRequest:TradeRequest) = {
      writeFraction (tradeRequest.value)
      writeTrade    (tradeRequest.tradeType)
      writeOption   (tradeRequest.delayUntil,
                     writeBoundary _)
    }

    def writeBoundary(boundary:Boundary) = {
      stream.writeBoolean (boundary.cmp.isEqual)
      stream.writeBoolean (boundary.cmp.isBelow)
      writeFraction(boundary.value)
    }

    def writeOptionalBoundary(boundary:Option[Boundary]) = writeOption(boundary, writeBoundary _)

    def writeOption[T](option:Option[T], writer:T=>Unit) = {
      val defined = option.isDefined
      stream.writeBoolean(defined)
      if (defined) writer(option.get)
    }
  }

  implicit def dataStream2CppInteractionStream(stream:DataOutputStream):CPPInteractionOutputStream =
    new CPPInteractionOutputStream(stream)
}

object HubProtocol {

  val InvalidId = -1

  import CPPIO.dataStream2CppInteractionStream

  sealed trait Packet


  case class RegisterTicksProvider(val key:String) extends Packet
  case class RegisterTradeConnector(val key:String,
                                    val balance:Fraction,
                                    val equity:Fraction) extends Packet

  // this response send when trying to register already registered resource
  case class AlreadyRegistered() extends Packet

  // this response sent when registered resource successfully
  case class Registered() extends Packet

  case class ResourceNotFound() extends Packet

  case class TickProviderConnected() extends Packet

  case class TradeConnectorConnected(val balance:Fraction,
                                     val equity:Fraction) extends Packet

  case class OnTick(val bid:Fraction, val ack:Fraction) extends Packet

  case class AskTicksProvider(val key:String) extends Packet
  case class AskTradeConnector(val key:String) extends Packet


  // trading protocol
  case class RequestNewId() extends Packet
  case class NewId(val id:Long) extends Packet {
    override def toString() = "NewId: " + id
  }

  case class FreeTrade(id:Long) extends Packet

  case class OpenTrade(val id:Long,
                       val request:TradeRequest,
                       val stopValue:Boundary,
                       val takeProfit:Option[Boundary]) extends Packet
  
  case class CloseRequest(val id:Long) extends Packet
  case class UpdateStopRequest(val id:Long, val stopValue:Boundary) extends Packet
  case class UpdateTakeProfitRequest(val id:Long, val stopValue:Option[Boundary]) extends Packet

  case class MessageAboutTrade(val id:Long, val message:String) extends Packet
  case class OpenedResponse(val id:Long) extends Packet
  case class ExternallyClosed(val id:Long) extends Packet

  case class CurrentBalance(val balance:Fraction) extends Packet
  case class CurrentEquity (val  equity:Fraction) extends Packet

  def writePacket(packet:Packet):Array[Byte] = {

    val byteStream = new ByteArrayOutputStream()

    IO.withStream(new DataOutputStream(byteStream),
                  (stream:DataOutputStream) => {

                    stream.writeClassName(packet)

                    packet match {
                      case RegisterTicksProvider(key) => {
                        stream.writeUTF8String(key)
                      }
                      case RegisterTradeConnector(key, balance, equity) => {
                        stream.writeUTF8String(key)
                        stream.writeFraction(balance)
                        stream.writeFraction(equity)
                      }
                      case Registered() => /* no data, do nothing */
                      case AlreadyRegistered() => /* no data, do nothing */
                      case OnTick(bid, ask) => {
                        stream.writeFraction(bid)
                        stream.writeFraction(ask)
                      }
                      case AskTicksProvider(key) => {
                        stream.writeUTF8String(key)
                      }
                      case AskTradeConnector(key) => {
                        stream.writeUTF8String(key)
                      }
                      case TickProviderConnected() => /* no data, do nothing */
                      case TradeConnectorConnected(balance, equity) => {
                        stream.writeFraction(balance)
                        stream.writeFraction(equity)
                      }
                      case ResourceNotFound() => /* no data, do nothing */
                      case RequestNewId() => /* no data, do nothing */
                      case NewId(id) => {
                        stream.writeLong(id)
                      }
                      case FreeTrade(id) => {
                        stream.writeLong(id)
                      }
                      case OpenTrade(id, request, stopValue, takeProfit) => {
                        stream.writeLong(id)
                        stream.writeTradeRequest(request)
                        stream.writeBoundary(stopValue)
                        stream.writeOptionalBoundary(takeProfit)
                      }
                      case CloseRequest(id) => stream.writeLong(id)
                      case UpdateStopRequest(id, stopValue) => {
                        stream.writeLong(id)
                        stream.writeBoundary(stopValue)
                      }
                        
                      case UpdateTakeProfitRequest(id, takeProfit) => {
                        stream.writeLong(id)
                        stream.writeOptionalBoundary(takeProfit)
                      }
                      case OpenedResponse(id) => stream.writeLong(id)
                      case ExternallyClosed(id) => stream.writeLong(id)

                      case CurrentBalance(balance) => stream.writeFraction(balance)
                      case CurrentEquity(equity) => stream.writeFraction(equity)

                      case MessageAboutTrade(id, message) => {
                        stream.writeLong(id)
                        stream.writeUTF8String(message)
                      }
                    }
                  })

    byteStream.toByteArray
  }

  def readPacket(buffer:Array[Byte]):Packet = {
    IO.withStream(new DataInputStream(new ByteArrayInputStream(buffer)),
                  (stream:DataInputStream) => {

                    stream.readUTF8String() match {
                      case "RegisterTicksProvider" => new RegisterTicksProvider(stream.readUTF8String())
                      case "RegisterTradeConnector" => new RegisterTradeConnector(stream.readUTF8String(),
                                                                                  stream.readFraction(),
                                                                                  stream.readFraction())
                      case "Registered" => new Registered()
                      case "AlreadyRegistered" => new AlreadyRegistered()
                      case "OnTick" => new OnTick(stream.readFraction(),
                                                  stream.readFraction())
                      case "AskTicksProvider" => new AskTicksProvider(stream.readUTF8String())
                      case "AskTradeConnector" => new AskTradeConnector(stream.readUTF8String())
                      case "TickProviderConnected" => new TickProviderConnected()
                      case "TradeConnectorConnected" => new TradeConnectorConnected(stream.readFraction(),
                                                                                    stream.readFraction())
                      case "ResourceNotFound" => new ResourceNotFound()
                      case "RequestNewId" => new RequestNewId()

                      case "NewId" => new NewId(stream.readLong())

                      case "FreeTrade" => new FreeTrade(stream.readLong())

                      case "OpenTrade" => new OpenTrade(stream.readLong(),
                                                        stream.readTradeRequest(),
                                                        stream.readBoundary(),
                                                        stream.readOptionalBoundary())

                      case "CloseRequest" => new CloseRequest(stream.readLong())

                      case "UpdateStopRequest" => new UpdateStopRequest(stream.readLong(),
                                                                        stream.readBoundary())

                      case "UpdateTakeProfitRequest" => new UpdateTakeProfitRequest(stream.readLong(),
                                                                                    stream.readOptionalBoundary())

                      case "OpenedResponse" => new OpenedResponse(stream.readLong())
                      case "ExternallyClosed" => new ExternallyClosed(stream.readLong())

                      case "CurrentBalance" => new CurrentBalance(stream.readFraction())
                      case "CurrentEquity" => new CurrentEquity(stream.readFraction())

                      case "MessageAboutTrade" => new MessageAboutTrade(stream.readLong(),
                                                                        stream.readUTF8String())

                      case wrongPacketName => throw new RuntimeException("Wrong packet: " + wrongPacketName)
                    }
                  })
  }

}
