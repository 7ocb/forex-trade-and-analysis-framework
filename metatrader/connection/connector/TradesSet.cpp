#include "TradesSet.h"
#include <algorithm>

TradesSet::TradesSet()
   : _monitor(Platform::instance().createMonitor()) {
   
}

std::list<Trade>::iterator TradesSet::findTradeIf(const std::function<bool(Trade)> &condition) {
   return std::find_if(_trades.begin(),
                       _trades.end(),
                       condition);
} 


void TradesSet::postModify(uint64 id,
                           const Modifier& modifier) {
   _monitor->lock();

   auto matcher = [id](const Trade &trade) -> bool {
      return id == trade.getId();
   };
   
   _modifications.push_back([=]() -> void {
         auto found = findTradeIf(matcher);

         if (found != _trades.end()) {
            modifier(*found);
         } 
      } );
   
   _monitor->unlock();
} 

void TradesSet::postAdd(const Trade &trade) {
   _monitor->lock();
   _modifications.push_back([=]() -> void {
         // TODO: check if trade with this id already present         
         _trades.push_back(trade);
      } );
   _monitor->unlock();
}

void TradesSet::postCloseAll() {
   _monitor->lock();
   _modifications.push_back([=]() -> void {
         for (auto trade : _trades) {
            trade.setIsWantsClose();
         } 
      } );
   _monitor->unlock();
} 

void TradesSet::applyModifications() {
   _monitor->lock();

   std::for_each(_modifications.begin(),
                 _modifications.end(),
                 [](std::function<void()>& action) -> void { action(); } );

   _modifications.clear();
   
   _monitor->unlock();
} 

Trade *TradesSet::tradeById(uint64 id) {
   auto found
      = findTradeIf([id](const Trade &trade) -> bool {
            return id == trade.getId();
         });

   if (found != _trades.end()) return &(*found);
   else return nullptr;
}

void TradesSet::removeTradeById(uint64 id) {

   auto found
      = findTradeIf([id](const Trade &trade) -> bool {
                        return id == trade.getId();
                     });

   if (found != _trades.end()) {
      _trades.erase(found);
   }
} 

std::list<uint64> TradesSet::idsOfActiveTrades() const {
   std::list<uint64> output;
   
   for (const Trade &trade : _trades) {
      output.push_back(trade.getId());
   }

   return output;
} 

TradesSet::~TradesSet() {
   delete _monitor;
} 

