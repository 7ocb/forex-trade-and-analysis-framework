#ifndef __2B5A6FE3DD5B95658C72A83EC7713EEE_MONITOR_H_INCLUDED__
#define __2B5A6FE3DD5B95658C72A83EC7713EEE_MONITOR_H_INCLUDED__

#include "common.h"

class Monitor {
public:

   virtual void lock() = 0;
   virtual void unlock() = 0;

   virtual void wait() = 0;
   virtual void wait(uint64 milliseconds) = 0;
   virtual void notify() = 0;
   
   virtual ~Monitor() {}
};


#endif 	// __2B5A6FE3DD5B95658C72A83EC7713EEE_MONITOR_H_INCLUDED__
