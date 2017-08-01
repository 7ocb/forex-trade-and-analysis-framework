#include "MTTradeConnector.h"
#include "platform.h"
#include "protocol.h"
#include "InputDataBuffer.h"
#include <iostream>
#include <stdio.h>
#include <sstream>

#define LOCK _synchronization->lock()
#define UNLOCK _synchronization->unlock()

MTTradeConnector::MTTradeConnector(RunLoop &runLoop,
                                   const std::string &address,
                                   int port,
                                   const std::string &key,
                                   double balance,
                                   double equity)
: RunLoopUser(runLoop)
, _balance(balance)
, _equity(equity)
, _synchronization(Platform::instance().createMonitor())
, _ids(0)
, _lastOrphanedId(0)
, _logger("trade", address, port, key)
, _key(key)
, _hubInteraction(runLoop,
                  _logger,
                  address,
                  port,
                  std::bind(&MTTradeConnector::onStartedConnection, this),
                  std::bind(&MTTradeConnector::onPacket, this, std::placeholders::_1),
                  std::bind(&MTTradeConnector::onDisconnect, this)) {


} 


MTTradeConnector::~MTTradeConnector() {
   delete _synchronization;
}

void MTTradeConnector::onStartedConnection() {
   _synchronization->lock();
   _hubInteraction.sendRawData(Protocol::RegisterTradeConnector(_key,
                                                                _balance,
                                                                _equity).buffer());
   _synchronization->unlock();
} 

void MTTradeConnector::onPacket(const std::string& buffer) {
   InputDataBuffer input(buffer);


   const std::string incomingPacketName = input.nextString();

   std::cout << "received packet: " << incomingPacketName << std::endl;

   if (incomingPacketName == Protocol::RequestNewId::NAME) {

      _hubInteraction.sendRawData(Protocol::NewId(++_ids).buffer());
   } else if (incomingPacketName == Protocol::OpenTrade::NAME) {
      Protocol::OpenTrade packet(input);

      packet.dump(std::cout);

      std::cout << std::endl;

      // put the requested trade to the list of trades

      const TradeRequest &request = packet.tradeRequest();
      
      _trades.postAdd(Trade(packet.id(),
                            request.tradeType(),
                            request.value(),
                            request.delay(),
                            packet.stopValue(),
                            packet.takeProfit()));
      
   } else if (incomingPacketName == Protocol::CloseRequest::NAME) {
      Protocol::CloseRequest packet(input);

      packet.dump(std::cout);

      std::cout << std::endl;

      _trades.postModify(packet.id(),
                         [](Trade &trade) -> void {
                            trade.setIsWantsClose();
                         } );
      
   } else if (incomingPacketName == Protocol::UpdateStopRequest::NAME) {
      Protocol::UpdateStopRequest packet(input);

      packet.dump(std::cout);

      std::cout << std::endl;

      _trades.postModify(packet.id(),
                         [packet](Trade &trade) -> void {
                            trade.setRequestedStop(packet.stopValue());
                         } );
      
   } else if (incomingPacketName == Protocol::UpdateTakeProfitRequest::NAME) {
      Protocol::UpdateTakeProfitRequest packet(input);

      packet.dump(std::cout);

      std::cout << std::endl;

      _trades.postModify(packet.id(),
                         [packet](Trade &trade) -> void {
                            trade.setRequestedTp(packet.takeProfitValue());
                         } );
   } 
}

void MTTradeConnector::StartNextTradesIteration() {
   _trades.applyModifications();
   _iterationIds = _trades.idsOfActiveTrades();
}

bool MTTradeConnector::ShiftToNextTrade() {
   if (_iterationIds.empty()) return false;
   
   _currentTrade = _trades.tradeById(_iterationIds.front());
   _iterationIds.pop_front();

   return _currentTrade != nullptr;
}

void MTTradeConnector::LogTradeConnectorMessage(const std::string message) {
   post([this, message]() -> void {
         _logger.log(message);
      });
}

void MTTradeConnector::TradeMessage(const std::string message) {

   if (_currentTrade == nullptr) return;
   
   uint64 currentTradeId = _currentTrade->getId();
   
   post([this, message, currentTradeId]() -> void {
         std::ostringstream line;
         line << "trade (" << currentTradeId << "): " << message;
         _logger.log(line.str());

         _hubInteraction.sendRawData(Protocol::MessageAboutTrade(currentTradeId,
                                                                 message).buffer());
      });
}

/**
 * If current trade is closed by the mql code, this means that it can be
 * deleted from the list of trades
 */ 
void MTTradeConnector::FreeTrade() {

   const uint64 currentTradeId = _currentTrade->getId();

   post([this, currentTradeId]() -> void {
         std::ostringstream line;
         line << "trade " << currentTradeId << " freed";
         _logger.log(line.str());

         if (currentTradeId > _lastOrphanedId) {
            _hubInteraction.sendRawData(Protocol::FreeTrade(currentTradeId).buffer());
         } 
      });
   
   
   _trades.removeTradeById(_currentTrade->getId());
}

void MTTradeConnector::TradeNotifyOpened() {
   const uint64 currentTradeId = _currentTrade->getId();
   
   post([this, currentTradeId]() -> void {
         if (currentTradeId > _lastOrphanedId) {
            _hubInteraction.sendRawData(Protocol::OpenedResponse(currentTradeId).buffer());
         }
      });
}

void MTTradeConnector::TradeNotifyClosed() {
   const uint64 currentTradeId = _currentTrade->getId();
   
   post([this, currentTradeId]() -> void {
         if (currentTradeId > _lastOrphanedId) {
            _hubInteraction.sendRawData(Protocol::ExternallyClosed(currentTradeId).buffer());
         } 
      });
}


void MTTradeConnector::UpdateBalance(double balance) {
   _synchronization->lock();
   
   _balance = balance;
   
   post([this, balance]() -> void {
         _hubInteraction.sendRawData(Protocol::CurrentBalance(balance).buffer());
      } );
   _synchronization->unlock();
}

void MTTradeConnector::UpdateEquity(double equity) {
   _synchronization->lock();

   _equity = equity;
   
   post([this, equity]() -> void {
         _hubInteraction.sendRawData(Protocol::CurrentEquity(equity).buffer());
      } );
   _synchronization->unlock();
}

void MTTradeConnector::onDisconnect() {
   // all trades become orphaned - no events to send about it and close as
   // fast as possible

   _lastOrphanedId = _ids;
   
   _trades.postCloseAll();
}
