#ifndef __CD021F925D4953D69758BEA97E0C4F76_NIXMONITOR_H_INCLUDED__
#define __CD021F925D4953D69758BEA97E0C4F76_NIXMONITOR_H_INCLUDED__

#include <pthread.h>
#include "platform.h"

class NixMonitor : public Monitor {
   NixMonitor(const NixMonitor &referenceToCopyFrom);
   void operator=(const NixMonitor &referenceToCopyFrom);

public:
   NixMonitor();

   void lock();
   void unlock();

   void wait();
   void wait(uint64 milliseconds);
   void notify();
   
   ~NixMonitor();

private:

   bool _locked;
   pthread_cond_t _condition;
   pthread_mutex_t _mutex;
};

#endif 	// __CD021F925D4953D69758BEA97E0C4F76_NIXMONITOR_H_INCLUDED__
