#ifndef __5B38EFCBEAF9BC48BC4BB89BC5A06F67_CONNECTION_H_INCLUDED__
#define __5B38EFCBEAF9BC48BC4BB89BC5A06F67_CONNECTION_H_INCLUDED__

#include <string>
#include "platform.h"

#include "RunLoopUser.h"
#include "ConnectionState.h"


// #include "StateError.h"
// #include "StateClosing.h"
// #include "StateClosed.h"
// #include "StateConnected.h"

// class ConnectionState;
// class ConnectionState::Context;
class ConnectionHandle;


class ConnectionHandle : public RunLoopUser {

   ConnectionHandle(const ConnectionHandle &referenceToCopyFrom);
   void operator=(const ConnectionHandle &referenceToCopyFrom);
   
public:

   ConnectionHandle(RunLoop& runLoop,
                    Logger& logger,
                    const std::string& addressString,
                    const int port,
                    // connection handle will not delete it's listener
                    ConnectionHandleListener &listener);

   void sendRawData(const std::string& buffer);

   // void close();

   virtual ~ConnectionHandle();

private: // methods
   /**
    * The only method can be called from different threads.
    *
    * However, no state should call this method if deleted
    */ 
   void postSwitchState(ConnectionState *targetState);

   // void switchToError();

   /**
    * Calls action while state change locked
    */ 
   void withCurrentState(std::function<void(ConnectionState *)> action);

   void switchState(ConnectionState *state);
   
private: // data

   ConnectionState *_nextState;
   ConnectionState *_currentState;
   ConnectionHandleListener &_listener;

   Monitor *_synchronization;
   
   ConnectionState::Context _stateContext;
};

#endif 	// __5B38EFCBEAF9BC48BC4BB89BC5A06F67_CONNECTION_H_INCLUDED__
