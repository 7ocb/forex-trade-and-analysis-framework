#ifndef __9EA460711E4601B02FF255E7D9195508_MTCONNECTOR_H_INCLUDED__
#define __9EA460711E4601B02FF255E7D9195508_MTCONNECTOR_H_INCLUDED__

#include "types.h"
#include "platform.h"
#include "RunLoop.h"
#include <map>

class MTTicksSink;
class MTTradeConnector;

#define TRADE_FORWARD_CALL(method)              \
   void method(int connectorId);

#define TRADE_FORWARD_GET(type, method)         \
   type method(int connectorId);

#define TRADE_FORWARD_SET(type, method)         \
   void method(int connectorId, type value);

#define TRADE_FORWARD_STRING(method)                            \
   void method(int connectorId, const std::string &value);

#define FORWARD_CURRENT_TRADE_SET(type, name)   \
   void TradeSet##name(int connectorId, const type &newValue);

#define FORWARD_CURRENT_TRADE_GET(type, name)   \
   type TradeGet##name(int connectorId);

#define FORWARD_CURRENT_TRADE_SET_BOUNDARY(name) FORWARD_CURRENT_TRADE_SET(Boundary, name)
#define FORWARD_CURRENT_TRADE_SET_OPT_BOUNDARY(name) FORWARD_CURRENT_TRADE_SET(Option<Boundary>, name)

#define FORWARD_CURRENT_TRADE_GET_BOUNDARY(name) FORWARD_CURRENT_TRADE_GET(Boundary, name)
#define FORWARD_CURRENT_TRADE_GET_OPT_BOUNDARY(name) FORWARD_CURRENT_TRADE_GET(Option<Boundary>, name)

class MTConnector {
public:
   MTConnector();

   // ticks sink

   int createTicksSink(const std::string address,
                       int port,
                       const std::string key);

   void sendTick(int id,
                 double bid,
                 double ask);

   void freeTicksSink(int id);

   // trade connector

   int createTradeConnector(const std::string address,
                            int port,
                            const std::string key,
                            double balance,
                            double equity);

   #include "trade.forwards.inc"
   #include "current.trade.access.inc"

   // int TradeGetType(int id);
   
   void freeTradeConnector(int id);


   ~MTConnector();
private:


   void forTradeConnector(int id,
                          const std::function<void(MTTradeConnector&)> &action);

   template
   <class RetVal> RetVal
   forTradeConnector(int id,
                     const std::function<RetVal(MTTradeConnector&)> &action);

   template
   <class RetVal> RetVal
   forTradeConnector(int id,
                     const std::function<RetVal(MTTradeConnector&)> &action,
                     const RetVal &defaultValue);

   std::function<void()> locked(const std::function<void()> action);

   void ctThread();

   RunLoop _ctRunLoop;

   Thread *_thread;
   Monitor *_synchronization;

   int _ids;

   std::map<int, MTTicksSink *> _tickSinks;
   std::map<int, MTTradeConnector *> _tradeConnectors;
};

#undef FORWARD_STRING
#undef TRADE_FORWARD_GET
#undef TRADE_FORWARD_SET
#undef TRADE_FORWARD_STRING
#undef TRADE_FORWARD_CALL
#undef FORWARD_CURRENT_TRADE_SET
#undef FORWARD_CURRENT_TRADE_GET
#undef FORWARD_CURRENT_TRADE_SET_BOUNDARY
#undef FORWARD_CURRENT_TRADE_SET_OPT_BOUNDARY
#undef FORWARD_CURRENT_TRADE_GET_BOUNDARY
#undef FORWARD_CURRENT_TRADE_GET_OPT_BOUNDARY

#endif 	// __9EA460711E4601B02FF255E7D9195508_MTCONNECTOR_H_INCLUDED__
