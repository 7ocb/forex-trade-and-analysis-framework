#include "StateConnected.h"
#include <stdio.h>
#include "InputDataBuffer.h"
#include "OutputDataBuffer.h"
#include <functional>






StateConnected::StateConnected(RunLoop& runLoop,
                  StateConnectedListener * listener,
                  Socket *connectedSocket,
                  const std::list<std::string> &delayedData)
   : _runLoop(runLoop)
   , _listener(listener)
   , _connectedSocket(connectedSocket)
   , _delayedData(delayedData)
   , _readThread(NULL)
   , _writeThread(NULL)
   , _synchronization(Platform::instance().createMonitor())
   , _closing(false)
   , _closed(false)
   , _error(false)
   , _reportedError(false) {

}

void StateConnected::init() {
   Platform &platform = Platform::instance();

   if ( ! _delayedData.empty() ) {
      _sendRunLoop.post(std::bind(&StateConnected::wtSendAllPending,
                                  this));
   }

   _leftThreads = 2;
   
   _readThread = platform.createThread(std::bind(&StateConnected::wtReadThreadMethod,
                                                 this));

   _writeThread = platform.createThread(std::bind(&StateConnected::wtWriteThreadMethod,
                                                  this));
}

void StateConnected::onError() {
   _synchronization->lock();

   if ( ! (_closed || _closing) ) {

      _error = true;
      _runLoop.post(std::bind(&StateConnected::ctReportError, this));
   }

   _synchronization->unlock();
}

void StateConnected::ctReportError() {
   if (_reportedError) return;
   _reportedError = true;

   _listener->onConnectedError();
}


void StateConnected::ctDeliverReceivedPacket() {
   std::list<std::string> deliver;

   _synchronization->lock();
   
   if ( ! _closing) {
      deliver = _receivedPackets;
      _receivedPackets.clear();
   } 
   
   _synchronization->unlock();

   for (auto i = deliver.begin();
        i != deliver.end();
        ++i) {
      _listener->onConnectedPacketReceived(*i);
   } 
}

void StateConnected::ctOnThreadEnded() {
   --_leftThreads;

   bool ended = _leftThreads == 0;

   // as both threads are ended, and posting the ctOnThreadEnded is the
   // last event posted in every thread, it is guaranteed that no more
   // events pending, so will report closed.

   if (ended) _listener->onConnectedClosed();
} 

void StateConnected::sendData(const std::string &buffer) {
   _synchronization->lock();

   _delayedData.push_back(buffer);

   if (! (_error || _closed || _closing)) {
      _sendRunLoop.post(std::bind(&StateConnected::wtSendAllPending,
                                  this));
   }

   _synchronization->unlock();
}

void StateConnected::onDataReceived(const std::string &data) {

}

void StateConnected::close() {
   _synchronization->lock();

   if ( ! _closing ) {

      _closing = true;
      _sendRunLoop.post(std::bind(&StateConnected::wtCloseSocket,
                                  this));
   }

   _synchronization->unlock();
}

void StateConnected::wtReadThreadMethod() {
   std::string buffer;
   while (receivePacket(_connectedSocket, buffer)) {
      _synchronization->lock();

      _receivedPackets.push_back(buffer);

      _runLoop.post(std::bind(&StateConnected::ctDeliverReceivedPacket,
                              this));

      _synchronization->unlock();
   }

   onError();
   _runLoop.post(std::bind(&StateConnected::ctOnThreadEnded, this));
}

void StateConnected::wtWriteThreadMethod() {
   _sendRunLoop.run();
   _runLoop.post(std::bind(&StateConnected::ctOnThreadEnded, this));
}

void StateConnected::wtCloseSocket() {
   _synchronization->lock();

   _connectedSocket->close();
   _sendRunLoop.terminate();

   _synchronization->unlock();
}

void StateConnected::wtSendAllPending() {
   _synchronization->lock();
   std::list<std::string> dataToSend = _delayedData;

   _delayedData.clear();

   bool doNothing = _error || _closed;

   _synchronization->unlock();

   if (doNothing) return;

   for (std::list<std::string>::iterator i = dataToSend.begin();
        i != dataToSend.end();
        ++i) {
      bool sendingFailed = ! sendPacket(_connectedSocket, *i);

      if (sendingFailed) {

         onError();

         return;
      }
   }
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
   delete _connectedSocket;

   if (_readThread != NULL) delete _readThread;
   if (_writeThread != NULL) delete _writeThread;

   delete _synchronization;
}
