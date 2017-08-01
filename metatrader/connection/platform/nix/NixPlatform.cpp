#include "NixPlatform.h"
#include <cstddef>
#include <sys/time.h>

#include "NixThread.h"
#include "NixMonitor.h"
#include "NixSocket.h"

#include <arpa/inet.h>
#include <unistd.h>

#include <endian.h>

NixPlatform::NixPlatform() {
   // nothing to do
} 

Socket *NixPlatform::createSocket() {
   return new NixSocket();
} 

Monitor *NixPlatform::createMonitor() {
   return new NixMonitor();
} 

Thread *NixPlatform::createThread(const Thread::Action &action) {
   return new NixThread(action);
} 

Platform::Milliseconds NixPlatform::currentTime() {
   timeval currentTime;

   gettimeofday(&currentTime, NULL);

   return currentTime.tv_sec * 1000L + currentTime.tv_usec / 1000L;
}

void NixPlatform::sleep(const Milliseconds time) {
   Milliseconds leftToSleep = time;

   while (leftToSleep > 0){

      if (leftToSleep < 1000) {
         usleep(leftToSleep * 1000);
         leftToSleep = 0;
      } else {
         usleep(999 * 1000);
         leftToSleep -= 999;
      } 
   } 
} 

int NixPlatform::htonl(int i) {
   return ::htonl(i);
}

uint64 NixPlatform::htonll(uint64 i) {
   return ::htobe64(i);
} 

int NixPlatform::ntohl(int i) {
   return ::ntohl(i);
}

uint64 NixPlatform::ntohll(uint64 i) {
   return ::be64toh(i);
} 

NixPlatform::~NixPlatform() {
   // nothing to do   
} 
