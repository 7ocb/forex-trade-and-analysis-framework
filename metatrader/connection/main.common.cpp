
#include <iostream>
#include <sstream>
#include <unistd.h>
#include <stdio.h>
#include <vector>
#include "protocol.h"
#include "RunLoop.h"
#include "ConnectionHandle.h"
#include "ConnectionHandleListener.h"

#include "MTConnector.h"

RunLoop *wtRunLoop;
RunLoop *ctRunLoop;


// class ConnectionHandleListenerTest : public ConnectionHandleListener {
//    void onPacket(const std::string& buffer) {
//       printf("packet received!\n");
//    } 

   
//    void onDisconnect() {
//       printf("disconnect received!\n");
//       // handle->close();
//       // delete handle;
//    }

//    void onConnectFailed() {
//       printf("connection fail received!\n");
      
//       // delete handle;
//    } 

//    // after this call it is safe to delete connection handle
//    // void onClosed(ConnectionHandle *handle) {
//    //    printf("deleted!\n");

//    //    delete handle;
//    //    ctRunLoop->terminate();
//    // }
   
// }
   // ;

void infiniteSleep() {
   for(;;) Platform::instance().sleep(1000);
} 

// void testConnectionHandle() {
//    ctRunLoop = new RunLoop();

//    ConnectionHandleListener *listener = new ConnectionHandleListenerTest();

//    // it is ok for it to be unused now
//    ConnectionHandle *handle = new ConnectionHandle(*ctRunLoop,
//                                                    "127.0.0.1",
//                                                    9101,
//                                                    *listener);

   
//    ctRunLoop->run();

//    delete ctRunLoop;

//    printf("handle is: %p", handle);
//    delete listener;
// }

void terminateCtRunLoop() {
   printf("terminating ct run loop\n");
   ctRunLoop->terminate();
} 

void threadFunction() {
   printf("thread started\n");
   // printf("executing in thread!\n");

   wtRunLoop->run();
   delete wtRunLoop;

   // ctRunLoop->post(terminateCtRunLoop);
   ctRunLoop->terminate();
   
   printf("thread ended\n");
} 

void printMessage(const char *message) {
   std::cout << "ms: " << Platform::instance().currentTime() << ", message: " << message << std::endl;
} 

void terminateRunLoop(RunLoop *loop) {
   printf("terminating some run loop\n");
   loop->terminate();
} 

void testThread() {
   wtRunLoop = new RunLoop();
   ctRunLoop = new RunLoop();
   Thread *t = Platform::instance().createThread(threadFunction);

   wtRunLoop->post(std::bind(&printMessage, "first"));
   wtRunLoop->post(std::bind(&printMessage, "second"));
   wtRunLoop->post(std::bind(&printMessage, "thread"));

   wtRunLoop->postDelayed(1000,
                        std::bind(&printMessage,
                                  "after second"));

   wtRunLoop->postDelayed(1000,
                        std::bind(&printMessage,
                                  "after second two"));

   wtRunLoop->postDelayed(3000,
                        std::bind(&printMessage,
                                  "after three seconds"));

   wtRunLoop->postDelayed(3000,
                          std::bind(&terminateRunLoop,
                                    wtRunLoop));

   ctRunLoop->run();

   printf("ct run loop terminated\n");

   delete t;
   delete ctRunLoop;
}

bool getString(std::string& buffer) {
   std::getline(std::cin,
                buffer);

   return buffer != "q";
} 

void testTicksSender() {
   ctRunLoop = new RunLoop();

   MTConnector *connector = new MTConnector();

   int id = connector->createTicksSink("127.0.0.1", 9101, "mt-test");

   connector->sendTick(id, 1.2);
   connector->sendTick(id, 1.2334);
   connector->sendTick(id, -1.2334);

   std::string buffer;
   
   while (getString(buffer)) {
      double tick;

      sscanf(buffer.c_str(),
             "%lf",
             &tick);

      std::cout << "got: " << buffer << std::endl;

      connector->sendTick(id, tick);
   } 

   delete connector;
   delete ctRunLoop;
}

void sleepTest() {

   Platform &platform = Platform::instance();
   
   std::cout << platform.currentTime() << ", sleeping 1 sec" << std::endl;

   platform.sleep(1000);

   std::cout << platform.currentTime() << ", done" << std::endl;
} 

void hardTestMTConnector() {
   // ctRunLoop = new RunLoop();

   MTConnector *connector = new MTConnector();

   const int sinksCount = 10;
   
   std::vector<int> sinks;

   for (int i = 0; i < sinksCount; ++i) {

      std::ostringstream name;
      name << "mt-test-" << i;
      
      const int sinkId = connector->createTicksSink("127.0.0.1", 9101, name.str());
      
      sinks.push_back(sinkId);
   }

   double price = 1.0;

   const Platform::Milliseconds minWaitTime = 10;
   const Platform::Milliseconds maxWaitTime = 30;
   Platform::Milliseconds waitTime = minWaitTime;

   Monitor *monitor = Platform::instance().createMonitor();

   // uint64 count = 0;

   Platform::instance().createThread([&]() -> void {
         while (true) {
            // monitor->lock();

            // std::cout << "dispatched " << count << " ticks" << std::endl;
            // printf("dispatched ")

            // monitor->unlock();

            Platform::instance().sleep(1000);
         } 
      } );
   
   
   // this test not expected to end


   for (int i = 0; i < sinksCount; ++i) {

      Platform::instance().createThread([i, &waitTime, &price, &sinks, connector, monitor]() -> void {
            while (true) {         
               const int sinkId = sinks[i];

               monitor->lock();
               double priceToSend = price;
               price += 0.0001;

               double timeToWait = waitTime;
               
               waitTime += 1;

               if (waitTime > maxWaitTime) {
                  waitTime = minWaitTime;
               }

               
               monitor->unlock();

               
               connector->sendTick(sinkId, priceToSend);

               // monitor->lock();
               // ++count;
               // monitor->unlock();


               Platform::instance().sleep(timeToWait);

               
            }
         } );

   }

   infiniteSleep();
   printf("infinite loop ended!!\n");
   
   // int id = connector->createTicksSink("127.0.0.1", 9101, "mt-test-1");
   // int id = connector->createTicksSink("127.0.0.1", 9101, "mt-test-2");
   // int id = connector->createTicksSink("127.0.0.1", 9101, "mt-test-3");
   // int id = connector->createTicksSink("127.0.0.1", 9101, "mt-test-4");
   // int id = connector->createTicksSink("127.0.0.1", 9101, "mt-test-5");
   // int id = connector->createTicksSink("127.0.0.1", 9101, "mt-test-6");

   // connector->sendTick(id, 1.2);
   // connector->sendTick(id, 1.2334);
   // connector->sendTick(id, -1.2334);

   // std::string buffer;

   // while (getString(buffer)) {
   //    double tick;

   //    sscanf(buffer.c_str(),
   //           "%lf",
   //           &tick);

   //    connector->sendTick(id, tick);
   // } 

   // delete connector;
   // delete ctRunLoop;
}


void testReconnecting() {
   MTConnector * connector = new MTConnector();

   int idOfConnector;
   int idOfSink;

   RunLoop runLoop;

   runLoop.postDelayed(1000,
                       [&]() -> void {
                          idOfSink = connector->createTicksSink("127.0.0.1", 9101, "test");
                       });


   runLoop.postDelayed(2000,
                       [&]() -> void {
                          idOfConnector = connector->createTradeConnector("127.0.0.1", 9101, "test", 100, 200);
                       });


   runLoop.postDelayed(3000,
                       [&]() -> void {
                          connector->freeTradeConnector(idOfConnector);
                          idOfConnector = connector->createTradeConnector("127.0.0.1", 9101, "test-2", 100, 200);
                          printf("created connector\n");
                       });

   runLoop.postDelayed(4000,
                       [&]() -> void {
                          connector->freeTradeConnector(idOfConnector);
                          idOfConnector = connector->createTradeConnector("127.0.0.1", 9101, "test-2", 100, 200);
                          printf("created connector\n");
                       });
   
   

   runLoop.run();
   
} 

void postDumpTrades(RunLoop *runLoop, MTConnector *connector, uint64 id) {
   runLoop->postDelayed(5000,
                        [=]() -> void {

                           connector->StartNextTradesIteration(id);

                           int index = 0;

                           std::cout << "current trades: " << std::endl;
                           
                           while (connector->ShiftToNextTrade(id)) {
                              std::cout << "trade " << index << ":" << std::endl;
                              std::cout << " value: " << connector->TradeGetValue(id) << std::endl;
                              std::cout << " requested stop: " << connector->TradeGetRequestedStop(id) << std::endl;
                              std::cout << " best set stop: " << connector->TradeGetBestSetStop(id) << std::endl;
                              std::cout << " requested tp: " << connector->TradeGetRequestedTp(id) << std::endl;
                              std::cout << " wants to be closed: " << connector->TradeGetIsWantsClose(id) << std::endl;

                              ++index;
                           } 
                            
                           postDumpTrades(runLoop, connector, id);
                        } );
} 

void testTradeConnector() {
   MTConnector *connector = new MTConnector();

   uint64 id = connector->createTradeConnector("127.0.0.1", 9101, "test", 400, 400);

   RunLoop *runLoop = new RunLoop();

   Platform::instance().createThread([=]() -> void {
         runLoop->run();
      } );

   postDumpTrades(runLoop, connector, id);

   infiniteSleep();
   printf("infinite loop ended!!\n");
} 

int main() {
   Platform::init(INIT_PLATFORM);

   std::cout << "uint64 size: " << sizeof(uint64) << std::endl;   
   std::cout << "long size: " << sizeof(long) << std::endl;
   std::cout << "long long size: " << sizeof(long long) << std::endl;
   
   
   // testThread();
   sleepTest();
   // hardTestMTConnector();
   // testTicksSender();
   // testConnectionHandle();
   // testTradeConnector();
   testReconnecting();

   
   
   Platform::cleanup();

   printf("exiting main\n");
   
   return 0;
} 
