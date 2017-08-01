#include "ConnectionState.h"

void ConnectionState::locked(const std::function<void()> &action) {
   Monitor *lock = _context.externalSynchronization;

   lock->lock();

   action();
   
   lock->unlock();
} 
