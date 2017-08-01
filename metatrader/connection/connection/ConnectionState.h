#ifndef __00DBA47363BD6C60F9371B85F61DDC11_CONNECTIONSTATE_H_INCLUDED__
#define __00DBA47363BD6C60F9371B85F61DDC11_CONNECTIONSTATE_H_INCLUDED__

#include <string>
#include <functional>
#include "logger.h"
#include "platform.h"
#include "RunLoop.h"

class ConnectionHandleListener;

class ConnectionState {
public:

   struct Context {

      typedef std::function<void(ConnectionState *)> StateSwitcher;
      
      Context(RunLoop &ctRunLoop,
              Logger &logger,
              Monitor *externalSynchronization,
              const StateSwitcher &stateSwitcher,
              ConnectionHandleListener &connectionListener)
         : ctRunLoop(ctRunLoop)
         , logger(logger)
         , externalSynchronization(externalSynchronization)
         , stateSwitcher(stateSwitcher)
         , connectionListener(connectionListener) {
      
      }
      
      RunLoop &ctRunLoop;
      Logger &logger;
      Monitor *externalSynchronization;
      const StateSwitcher stateSwitcher;
      ConnectionHandleListener &connectionListener;
   };
      
   ConnectionState(const Context &context)
      : _context(context) {}


   virtual void initState() = 0;

   virtual void sendData(const std::string &buffer) = 0;
   // virtual void onDataReceived(const std::string &data) = 0;

   virtual bool shouldDeliverEvents() = 0;

   virtual ~ConnectionState() {};

protected:

   void locked(const std::function<void()> &action);
   
   const Context _context;
};

#endif 	// __00DBA47363BD6C60F9371B85F61DDC11_CONNECTIONSTATE_H_INCLUDED__
