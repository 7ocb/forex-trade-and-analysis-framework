#ifndef __CD021F925D4953D69758BEA97E0C4F76_WINTHREAD_H_INCLUDED__
#define __CD021F925D4953D69758BEA97E0C4F76_WINTHREAD_H_INCLUDED__

#include "windows.h"
#include "platform.h"

class WinThread : public Thread {
   WinThread(const WinThread &referenceToCopyFrom);
   void operator=(const WinThread &referenceToCopyFrom);

public:
   WinThread(const Action &action);

   ~WinThread();
private:
   HANDLE _thread;
};

#endif 	// __CD021F925D4953D69758BEA97E0C4F76_WINTHREAD_H_INCLUDED__
