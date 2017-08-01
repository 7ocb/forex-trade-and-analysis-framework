#ifndef __CD021F925D4953D69758BEA97E0C4F76_NIXTHREAD_H_INCLUDED__
#define __CD021F925D4953D69758BEA97E0C4F76_NIXTHREAD_H_INCLUDED__

#include "platform.h"
#include <pthread.h>

class NixThread : public Thread {
   NixThread(const NixThread &referenceToCopyFrom);
   void operator=(const NixThread &referenceToCopyFrom);

public:
   NixThread(const Action &action);

   ~NixThread();
private:
   
   pthread_t _thread;
   pthread_attr_t _attr;
};

#endif 	// __CD021F925D4953D69758BEA97E0C4F76_NIXTHREAD_H_INCLUDED__
