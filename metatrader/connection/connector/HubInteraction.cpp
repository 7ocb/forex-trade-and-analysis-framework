#include "HubInteraction.h"

enum {
   RETRY_INTERVAL_MS = 5000
};

HubInteraction::HubInteraction(RunLoop &runLoop,
                               Logger &logger,
                               const std::string &address,
                               int port,
                               const EventReceiver &onRestarted,
                               const PacketReceiver &onPacket,
                               const EventReceiver &onDisconnected)
   : RunLoopUser(runLoop)
   , _logger(logger)
   , _address(address)
   , _port(port)
   , _connection(nullptr)
   , _onRestarted(onRestarted)
   , _onPacket(onPacket)
   , _onDisconnected(onDisconnected) {

   startConnecting();
} 

HubInteraction::~HubInteraction() {

   freeConnection();
}

void HubInteraction::freeConnection() {
   if (_connection != nullptr) {
      delete _connection;
      _connection = nullptr;
   } 
} 

void HubInteraction::startConnecting() {
   _logger.log("starting connection");

   freeConnection();

   _connection = new ConnectionHandle(runLoop(),
                                      _logger,
                                      _address,
                                      _port,
                                      *this);

   if (_onRestarted) _onRestarted();
} 

void HubInteraction::handleConnectionFailure(bool isDisconnect) {
   freeConnection();

   if (isDisconnect && _onDisconnected) _onDisconnected();
   
   postDelayed(RETRY_INTERVAL_MS,
               std::bind(&HubInteraction::startConnecting,
                         this));   
} 


bool HubInteraction::haveConnection() {
   return _connection != nullptr;
}

void HubInteraction::sendRawData(const std::string &data) {
   if (haveConnection()) {
      _connection->sendRawData(data);
   } 
} 

void HubInteraction::onPacket(const std::string& buffer) {
   if (_onPacket) {
      _onPacket(buffer);
   } else {
      _logger.log("Warning: packet ignored, as no packet handler installed.");
   } 
}

void HubInteraction::onConnectFailed() {
   _logger.log("connection failed");

   handleConnectionFailure(false);
}

void HubInteraction::onDisconnect() {
   _logger.log("connection lost");

   handleConnectionFailure(false);
}
