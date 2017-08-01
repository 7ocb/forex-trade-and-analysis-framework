#include "MTTicksSink.h"
#include "MTTradeConnector.h"
#include "MTConnector.h"

#define TRADE_FORWARD_CALL(method)                                      \
   void MTConnector::method(int connectorId) {                          \
      return forTradeConnector(connectorId,                             \
                               [=](MTTradeConnector &connector) -> void { \
                                  connector.method();                   \
                               } );                                     \
   }


#define TRADE_FORWARD_GET(type, method)                                 \
   type MTConnector::method(int connectorId) {                          \
      return forTradeConnector<type>(connectorId,                       \
                                     [=](MTTradeConnector &connector) -> type { \
                                        return connector.method();      \
                                     });                                \
                                     }

#define TRADE_FORWARD_SET(type, method)                                 \
   void MTConnector::method(int connectorId, type data) {               \
      return forTradeConnector(connectorId,                             \
                               [=](MTTradeConnector &connector) -> void { \
                                  connector.method(data);               \
                               });                                      \
   }

#define TRADE_FORWARD_STRING(method)                                    \
   void MTConnector::method(int connectorId, const std::string &value) { \
      return forTradeConnector(connectorId,                             \
                               [=](MTTradeConnector &connector) -> void { \
                                  connector.method(value);              \
                               });                                      \
   } 


#define FORWARD_CURRENT_TRADE_SET(type, name)                           \
   void MTConnector::TradeSet##name(int connectorId, const type &newValue) { \
      forTradeConnector(connectorId,                                    \
                        [=](MTTradeConnector &connector) -> void {      \
                           connector.TradeSet##name(newValue);          \
                        } );                                            \
   } 

#define FORWARD_CURRENT_TRADE_GET(type, name)                           \
   type MTConnector::TradeGet##name(int connectorId) {                  \
      return forTradeConnector<type>(connectorId,                       \
                                     [=](MTTradeConnector &connector) -> type { \
                                        return connector.TradeGet##name(); \
                                     });                                \
                                     } 

#define FORWARD_CURRENT_TRADE_SET_BOUNDARY(name) FORWARD_CURRENT_TRADE_SET(Boundary, name)
#define FORWARD_CURRENT_TRADE_SET_OPT_BOUNDARY(name) FORWARD_CURRENT_TRADE_SET(Option<Boundary>, name)

#define FORWARD_CURRENT_TRADE_GET_BOUNDARY(name) FORWARD_CURRENT_TRADE_GET(Boundary, name)
#define FORWARD_CURRENT_TRADE_GET_OPT_BOUNDARY(name) FORWARD_CURRENT_TRADE_GET(Option<Boundary>, name)


MTConnector::MTConnector() {
   
   _thread = Platform::instance().createThread(std::bind(&MTConnector::ctThread, this));

   _synchronization = Platform::instance().createMonitor();

   _ids = 0;
}

int MTConnector::createTicksSink(const std::string inAddress,
                                 int port,
                                 const std::string inKey) {
   
   _synchronization->lock();

   const std::string address = Thread::threadSafeCopy(inAddress);
   const std::string key = Thread::threadSafeCopy(inKey);
   
   int idOfSink = _ids++;

   _ctRunLoop.post(locked([=]() -> void {
         this->_tickSinks[idOfSink] = new MTTicksSink(this->_ctRunLoop,
                                                      address,
                                                      port,
                                                      key);
      }));
 
   _synchronization->unlock();
   
   return idOfSink;
} 

void MTConnector::sendTick(int id,
                           double bid,
                           double ask) {

   _synchronization->lock();

   _ctRunLoop.post(locked([=]() -> void {
         auto sink = this->_tickSinks.find(id);
         if (sink != this->_tickSinks.end()) {
            (*sink).second->sendTick(bid, ask);
         } 
      } ));
   
   _synchronization->unlock();
} 
   
void MTConnector::freeTicksSink(int id) {
   _synchronization->lock();

   _ctRunLoop.post(locked([=]() -> void {
         const auto notFound = this->_tickSinks.end();
         auto sink = this->_tickSinks.find(id);

         if (sink != notFound) {
            auto connector = (*sink).second;

            delete connector;

            this->_tickSinks.erase(sink);
         }
      }) );

   _synchronization->unlock();
}


int MTConnector::createTradeConnector(const std::string inAddress,
                                      int port,
                                      const std::string inKey,
                                      double balance,
                                      double equity) {
   _synchronization->lock();

   const std::string address = Thread::threadSafeCopy(inAddress);
   const std::string key = Thread::threadSafeCopy(inKey);
   
   const int idOfConnector = _ids++;

   _ctRunLoop.post(locked([=]() -> void {
         this->_tradeConnectors[idOfConnector]
            = new MTTradeConnector(this->_ctRunLoop,
                                   address,
                                   port,
                                   key,
                                   balance,
                                   equity);
      }) );
   
   _synchronization->unlock();

   return idOfConnector;
} 

#include "trade.forwards.inc"
#include "current.trade.access.inc"

void MTConnector::freeTradeConnector(int id) {
   _synchronization->lock();

   _ctRunLoop.post(locked([=]() -> void {
         const auto notFound = this->_tradeConnectors.end();
         auto sink = this->_tradeConnectors.find(id);

         if (sink != notFound) {
            auto connector = (*sink).second;

            delete connector;
            
            this->_tradeConnectors.erase(sink);
         }
      }) );
   
   _synchronization->unlock();
}

void MTConnector::forTradeConnector(int id,
                       const std::function<void(MTTradeConnector&)> &action) {
   _synchronization->lock();

   auto connector = _tradeConnectors.find(id);

   if (connector != _tradeConnectors.end()) {
      action(*(connector->second));
   }
   
   _synchronization->unlock();
} 

template
<class RetVal> RetVal
MTConnector::forTradeConnector(int id,
                               const std::function<RetVal(MTTradeConnector&)> &action) {
   return forTradeConnector<RetVal>(id, action, RetVal());
}

template
<class RetVal> RetVal
MTConnector::forTradeConnector(int id,
                               const std::function<RetVal(MTTradeConnector&)> &action,
                               const RetVal &defaultValue) {
   _synchronization->lock();
   auto connector = _tradeConnectors.find(id);

   auto value = defaultValue;
   
   if (connector != _tradeConnectors.end()) {
      value = action(*(connector->second));
   }

   _synchronization->unlock();
   
   return value;
} 

std::function<void()> MTConnector::locked(const std::function<void()> action) {
   return [=]() -> void {
      _synchronization->lock();
      action();
      _synchronization->unlock();
   };
} 


void MTConnector::ctThread() {
   printf("ct thread started\n");
   _ctRunLoop.run();
   printf("ct thread stopped\n");
}


MTConnector::~MTConnector() {
   delete _synchronization;

   _ctRunLoop.terminate();

   Thread::joinAndDelete(_thread);
} 
