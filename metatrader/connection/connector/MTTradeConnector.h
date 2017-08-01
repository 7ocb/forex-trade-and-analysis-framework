#ifndef __9EA460711E4601B02FF255E7D9195508_MTTRADECONNECTOR_H_INCLUDED__
#define __9EA460711E4601B02FF255E7D9195508_MTTRADECONNECTOR_H_INCLUDED__

#include "logger.h"
#include "HubInteraction.h"
#include "TradesSet.h"
#include "RunLoopUser.h"

class Monitor;

#define FORWARD_CURRENT_TRADE_GET(type, name) \
   type TradeGet##name() const { return _currentTrade->get##name(); }

#define FORWARD_CURRENT_TRADE_SET(type, name) \
   void TradeSet##name(const type& newValue) { _currentTrade->set##name(newValue); }

#define FORWARD_CURRENT_TRADE_SET_BOUNDARY(name) FORWARD_CURRENT_TRADE_SET(Boundary, name)
#define FORWARD_CURRENT_TRADE_SET_OPT_BOUNDARY(name) FORWARD_CURRENT_TRADE_SET(Option<Boundary>, name)

#define FORWARD_CURRENT_TRADE_GET_BOUNDARY(name) FORWARD_CURRENT_TRADE_GET(Boundary, name)
#define FORWARD_CURRENT_TRADE_GET_OPT_BOUNDARY(name) FORWARD_CURRENT_TRADE_GET(Option<Boundary>, name)

class MTTradeConnector : private RunLoopUser {
public:

   MTTradeConnector(RunLoop &runLoop,
                    const std::string &address,
                    int port,
                    const std::string &key,
                    double balance,
                    double equity);

   /* these methods should be called from mt's thread */
   
   /* mt thread */ void StartNextTradesIteration();
   /* mt thread */ bool ShiftToNextTrade();
   /* mt thread */ void FreeTrade();
   /* mt thread */ void TradeNotifyOpened();
   /* mt thread */ void TradeNotifyClosed();
   /* mt thread */ void UpdateBalance(double balance);
   /* mt thread */ void UpdateEquity(double equity);
   /* mt thread */ void LogTradeConnectorMessage(const std::string message);
   /* mt thread */ void TradeMessage(const std::string message);
   /* mt thread */ #include "current.trade.access.inc"
   
   // void terminateAndFree();

   ~MTTradeConnector();
private:

   void LogMessage();
   
   void onStartedConnection();
   void onPacket(const std::string& buffer);
   void onDisconnect();
   // void onClosed();
   
private:
   TradesSet _trades;

   double _balance;
   double _equity;
   
   Monitor *_synchronization;

   uint64 _ids;
   uint64 _lastOrphanedId;

   std::list<uint64> _iterationIds;
   Trade *_currentTrade;

   Logger _logger;

   
   const std::string _key;
   HubInteraction _hubInteraction;
};

#undef FORWARD_CURRENT_TRADE_SET                
#undef FORWARD_CURRENT_TRADE_GET
#undef FORWARD_CURRENT_TRADE_SET_BOUNDARY
#undef FORWARD_CURRENT_TRADE_SET_OPT_BOUNDARY
#undef FORWARD_CURRENT_TRADE_GET_BOUNDARY
#undef FORWARD_CURRENT_TRADE_GET_OPT_BOUNDARY


#endif 	// __9EA460711E4601B02FF255E7D9195508_MTTRADECONNECTOR_H_INCLUDED__
