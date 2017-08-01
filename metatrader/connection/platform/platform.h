#ifndef __5B38EFCBEAF9BC48BC4BB89BC5A06F67_PLATFORM_H_INCLUDED__
#define __5B38EFCBEAF9BC48BC4BB89BC5A06F67_PLATFORM_H_INCLUDED__

#include "common.h"
#include <functional>
#include "Monitor.h"
#include "Socket.h"
#include "Thread.h"

class Platform {
public:

   typedef uint64 Milliseconds;
   
   static void init(Platform *);
   static void cleanup();
   
   static Platform& instance();

   virtual Socket *createSocket() = 0;

   virtual Monitor *createMonitor() = 0;

   virtual Thread *createThread(const Thread::Action &action) = 0;

   virtual Milliseconds currentTime() = 0;

   virtual void sleep(const Milliseconds time) = 0;

   virtual int htonl(int ) = 0;
   virtual uint64 htonll(uint64 ) = 0;

   virtual int ntohl(int ) = 0;
   virtual uint64 ntohll(uint64 ) = 0;

   virtual ~Platform() {}
private:

   static Platform *_instance;
};


#endif 	// __5B38EFCBEAF9BC48BC4BB89BC5A06F67_PLATFORM_H_INCLUDED__
