package tests.hub

import tas.hub.HubProtocol

import org.scalatest.FlatSpec
import org.scalamock.scalatest.MockFactory

import tas.service.ConnectionHandle

import tas.events.SyncCallSubscription

import tas.hub.clients.RemoteTradeBackend
import tas.hub.BindClientConnectionToRemoteBackend

import tas.types.Fraction

import tas.output.logger.{
  ScreenLogger,
  NullLogger
}

class BindClientConnectionToRemoteBackendTests
    extends FlatSpec
    with MockFactory {

  import HubProtocol._

  val logger = ScreenLogger
  type PacketData = Array[Byte]

  behavior of "BindClientConnectionToRemoteBackend"

  class Test {
    val client = mock[ConnectionHandle]

    val onClientLost = mock[(BindClientConnectionToRemoteBackend)=>Unit]

    val remoteBackend = mock[RemoteTradeBackend]

    val equityMayBeChanged = new SyncCallSubscription
    val balanceMayBeChanged = new SyncCallSubscription

    var onPacket:(PacketData)=>Unit = null
    var onDisconnect:()=>Unit = null

    (client.setHandlers _).expects(*, *).onCall((packet, disconnect) => {
                                                  onPacket = packet
                                                  onDisconnect = disconnect
                                                } )

    (remoteBackend.balanceMayBeChanged _).expects().returning(balanceMayBeChanged)

    (remoteBackend.equityMayBeChanged _).expects().returning(equityMayBeChanged)


    val binding = new BindClientConnectionToRemoteBackend(logger,
                                                          client,
                                                          remoteBackend,
                                                          onClientLost)
  }

  it should "don't propagate any events if lost connection to client " in new Test {


    (onClientLost.apply _).expects(binding)


    {(remoteBackend.balance _).expects().returning(Fraction("1"))}
    {(remoteBackend.balance _).expects().returning(Fraction("2"))}

    (client.sendRawData _).expects(*)
      .onCall((packetData:PacketData) => {
                assert(readPacket(packetData) === CurrentBalance("1"))
              } )

    balanceMayBeChanged()

    (client.close _).expects()

    onDisconnect()

    // it should not call sendRawData, because connection already lost
    balanceMayBeChanged()
  }

}
