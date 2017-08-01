#ifndef __00DBA47363BD6C60F9371B85F61DDC11_STATECONNECTED_H_INCLUDED__
#define __00DBA47363BD6C60F9371B85F61DDC11_STATECONNECTED_H_INCLUDED__

#include <string>
#include "platform.h"
#include "ConnectionState.h"
#include "RunLoop.h"

class PingerListener {
public:
   virtual void onPingTimedOut() = 0;
   virtual void sendPing() = 0;
   virtual ~PingerListener() {}
};

class Pinger;

class StateConnectedListener {
public:

   virtual void onConnectedError() = 0;
   virtual void onConnectedClosed() = 0;
   virtual void onConnectedPacketReceived(const std::string &packet) = 0;
   
   virtual ~StateConnectedListener() {}
};

class StateConnected : public ConnectionState {
public:
   StateConnected(RunLoop& runLoop,
                  StateConnectedListener * listener,
                  Socket *connectedSocket,
                  const std::list<std::string> &delayedData);

   void init();

   void onError();
   void sendData(const std::string &buffer);
   void onDataReceived(const std::string &data);
   void close();

   virtual ~StateConnected();

private:

   void wtReadThreadMethod();
   void wtWriteThreadMethod();

   void wtSendAllPending();
   void wtCloseSocket();

   void ctOnThreadEnded();
   void ctReportError();
   void ctDeliverReceivedPacket();

   static bool sendPacket(Socket *socket, const std::string &data);
   static bool receivePacket(Socket *socket, std::string &data);
   
private:
   
   RunLoop& _runLoop;
   RunLoop _sendRunLoop;
   
   StateConnectedListener * _listener;
   Socket *_connectedSocket;
   std::list<std::string> _delayedData;

   int _leftThreads;
   
   Thread *_readThread;
   Thread *_writeThread;
   
   Monitor *_synchronization;

   bool _closing;
   bool _closed;
   bool _error;
   bool _reportedError;

   std::list<std::string> _receivedPackets;
};

#endif 	// __00DBA47363BD6C60F9371B85F61DDC11_STATECONNECTED_H_INCLUDED__
