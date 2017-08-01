#ifndef __CD021F925D4953D69758BEA97E0C4F76_WINMONITOR_H_INCLUDED__
#define __CD021F925D4953D69758BEA97E0C4F76_WINMONITOR_H_INCLUDED__

#include <windows.h>
#include "platform.h"
#include "FairMonitor.h"

class WinMonitor : public Monitor {
   WinMonitor(const WinMonitor &referenceToCopyFrom);
   void operator=(const WinMonitor &referenceToCopyFrom);

public:
   WinMonitor();

   void lock();
   void unlock();

   void wait();
   void wait(uint64 milliseconds);
   void notify();
   
   ~WinMonitor();

private:
   FairMonitor _monitor;
};

#endif 	// __CD021F925D4953D69758BEA97E0C4F76_WINMONITOR_H_INCLUDED__
