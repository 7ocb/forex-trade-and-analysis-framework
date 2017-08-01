#include "StateConnected.h"
#include <assert.h>
#include <stdio.h>
#include <functional>
#include "Pinger.h"
#include "InputDataBuffer.h"
#include "OutputDataBuffer.h"
#include "ConnectionHandleListener.h"

#define PING_TIMEOUT_MS 7000
#define PING_INTERVAL_MS 1000

#include "StateDisconnected.h"

StateConnected::StateConnected(const Context &context,
                               Socket *socket,
                               const std::list<std::string> &delayedData)
   : ConnectionState(context)
   , RunLoopUser(context.ctRunLoop)
   , _readThread(nullptr)
   , _writeThread(nullptr)
   , _closed(false)
   , _pinger(context.ctRunLoop, *this)
   , _socket(socket)
   , _delayedData(delayedData) {

}


void StateConnected::sendData(const std::string &buffer) {
   const std::string bufferToSend = Thread::threadSafeCopy(buffer);
   
   _sendRunLoop.post([=]() -> void {
         bool failed = !sendPacket(_socket,
                                   bufferToSend);

         if (failed) {
            switchToErrorIfNotClosed();
         } 
      });
} 

void StateConnected::initState() {
   post([=]() -> void {
         _context.logger.log("connected");
      });

   for (auto packet : _delayedData) {
      sendData(packet);
   }

   Platform &platform = Platform::instance();
   
   _readThread = platform.createThread(std::bind(&StateConnected::wtReadThreadMethod,
                                                 this));

   _writeThread = platform.createThread(std::bind(&StateConnected::wtWriteThreadMethod,
                                                  this));
}

void StateConnected::onPingTimedOut() {
   switchToErrorIfNotClosed();
}

void StateConnected::sendPing() {
   sendData(Pinger::PING_PACKET);
}

void StateConnected::wtReadThreadMethod() {
   std::string buffer;
   while (receivePacket(_socket, buffer)) {

      const std::string delivered = Thread::threadSafeCopy(buffer);
      
      post([this, delivered]() -> void {
            if (!_pinger.isPing(delivered)) {
               _context.connectionListener.onPacket(delivered);
            } 
         } );
   }

   switchToErrorIfNotClosed();
}

void StateConnected::wtWriteThreadMethod() {

   _sendRunLoop.run();
   
   switchToErrorIfNotClosed();
}


void StateConnected::close() {
   locked([this]() -> void {
         if (_closed) return;

         _closed = true;
         _socket->close();
         _sendRunLoop.terminate();
      });
} 

void StateConnected::switchToErrorIfNotClosed() {
   locked([this]() -> void {
         if (_closed) return;

         close();
         
         _context.stateSwitcher(new StateDisconnected(_context));
      });
}

bool StateConnected::sendPacket(Socket *socket,
                                const std::string &data) {
   
   std::string binSize = OutputDataBuffer().putInt(data.size()).buffer();

   if (data.size() > 0) {
      return socket->write(binSize)
         && socket->write(data);
   } else {
      return socket->write(binSize);
   } 
}

bool StateConnected::receivePacket(Socket *socket,
                                   std::string &data) {

   std::string binSize;

   if ( ! socket->read(binSize, 4) ) return false;

   unsigned int size = InputDataBuffer(binSize).nextInt();

   if (size > 0) {
      return socket->read(data, size);
   } else {
      data.clear();
      return true;
   } 
}

StateConnected::~StateConnected() {
   close();

   Thread::joinAndDelete(_readThread);
   Thread::joinAndDelete(_writeThread);

   delete _socket;
}
