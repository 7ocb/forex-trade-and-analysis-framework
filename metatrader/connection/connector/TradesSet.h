#ifndef __9EA460711E4601B02FF255E7D9195508_TRADESSET_H_INCLUDED__
#define __9EA460711E4601B02FF255E7D9195508_TRADESSET_H_INCLUDED__

#include <list>
#include "Trade.h"
#include "platform.h"

class TradesSet {
   TradesSet(const TradesSet &referenceToCopyFrom);
   void operator=(const TradesSet &referenceToCopyFrom);
public:

   TradesSet();

   typedef std::function<void(Trade&)> Modifier;

   // these methods to modify list from connector's thread
   void postModify(uint64 id,
                   const Modifier& modifier);

   void postAdd(const Trade &trade);

   void postCloseAll();

   // these methods to access and modify list from mt's thread
   void applyModifications();

   Trade *tradeById(uint64 id);
   void removeTradeById(uint64 id);

   std::list<uint64> idsOfActiveTrades() const;

   ~TradesSet();
   
private:

   std::list<Trade>::iterator findTradeIf(const std::function<bool(Trade)> &condition);
   
   std::list<Trade> _trades;
   std::list<std::function<void()> > _modifications;

   Monitor *_monitor;
};

#endif 	// __9EA460711E4601B02FF255E7D9195508_TRADESSET_H_INCLUDED__
