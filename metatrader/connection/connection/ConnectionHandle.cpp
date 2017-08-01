#include "ConnectionHandle.h"
#include <stdio.h>
#include "ConnectionHandleListener.h"
#include "StateConnecting.h"
#include "StateClosed.h"


ConnectionHandle::ConnectionHandle(RunLoop& runLoop,
                                   Logger& logger,
                                   const std::string& addressString,
                                   const int port,
                                   ConnectionHandleListener &listener)
   : RunLoopUser(runLoop)
   , _nextState(NULL)
   , _currentState(NULL)
   , _listener(listener)
   , _synchronization(Platform::instance().createMonitor())
   , _stateContext(ConnectionState::Context(runLoop,
                                            logger,
                                            _synchronization,
                                            std::bind(&ConnectionHandle::postSwitchState,
                                                      this,
                                                      std::placeholders::_1),
                                            listener)) {

   switchState(new StateConnecting(_stateContext,
                                   addressString,
                                   port));
   
}

void ConnectionHandle::withCurrentState(std::function<void(ConnectionState *)> action) {
   _synchronization->lock();

   if (_nextState) {
      action(_nextState);
   } else if (_currentState) {
      action(_currentState);
   }
   
   _synchronization->unlock();
}

void ConnectionHandle::sendRawData(const std::string& buffer) {
   withCurrentState([=](ConnectionState *state) -> void {
         state->sendData(buffer);
      } );
} 

ConnectionHandle::~ConnectionHandle() {
   if (_nextState)    delete _nextState;
   if (_currentState) delete _currentState;

   delete _synchronization;
} 

void ConnectionHandle::postSwitchState(ConnectionState *nextState) {
   _synchronization->lock();

   bool switchingInProgress = _nextState != NULL;

   if (_nextState != NULL) delete _nextState;

   _nextState = nextState;

   bool switchNeeded = _nextState != NULL;

   if (!switchingInProgress && switchNeeded) {
      post([=]() -> void {
            _synchronization->lock();

            ConnectionState *willSwitchTo = _nextState;
            _nextState = NULL;
            _synchronization->unlock();
            
            switchState(willSwitchTo);

         } );
   } 
   

   _synchronization->unlock();
}


void ConnectionHandle::switchState(ConnectionState *state) {
   ConnectionState* previousState = _currentState;

   _currentState = state;

   if (previousState) delete previousState;   

   if (_currentState) _currentState->initState();
} 


