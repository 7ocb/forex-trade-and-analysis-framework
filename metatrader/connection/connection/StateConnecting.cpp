#include "StateConnecting.h"
#include <assert.h>
#include <stdio.h>
#include <functional>
#include "StateConnected.h"
#include "StateConnectFailed.h"

StateConnecting::StateConnecting(const Context &context,
                                 const std::string& addressString,
                                 const int port)
   : ConnectionState(context)
   , _address(addressString)
   , _port(port)
   , _socket(Platform::instance().createSocket())
   , _thread(nullptr)
   , _closed(false)
{

}

void StateConnecting::initState() {
   _thread = Platform::instance().createThread(std::bind(&StateConnecting::connectThreadMethod,
                                                         this));
}

void StateConnecting::connectThreadMethod() {

   bool connected = _socket->connect(_address,
                                     _port);

   locked([=]() -> void {

         if (connected) {

            Socket *connectedSocket = _socket;
            _socket = nullptr;
            
            _context.stateSwitcher(new StateConnected(_context,
                                                      connectedSocket,
                                                      _delayedData));

         } else {

            if (!_closed) {
               _context.stateSwitcher(new StateConnectFailed(_context));
            }
         }
      } );
}

void StateConnecting::sendData(const std::string &buffer) {

   const std::string dataToSend = Thread::threadSafeCopy(buffer);
   
   locked([=]() -> void {
         assert(_socket);
         _delayedData.push_back(dataToSend);
      } );
}

StateConnecting::~StateConnecting() {
   locked([=]() -> void {

         _closed = true;
         if (_socket) _socket->close();
         
      } );

   Thread::joinAndDelete(_thread);

   if (_socket) {
      delete _socket;
   }
}
