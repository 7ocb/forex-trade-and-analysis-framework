#include "WinPlatform.h"
#include <cstddef>
#include <sys/time.h>

#include "winsock2.h"
#include "WinThread.h"
#include "WinMonitor.h"
#include "WinSocket.h"

WinPlatform::WinPlatform() {
   WSADATA wsaData = {0};
   WSAStartup(MAKEWORD(2, 2), &wsaData);
} 

Socket *WinPlatform::createSocket() {
   return new WinSocket();
} 

Monitor *WinPlatform::createMonitor() {
   return new WinMonitor();
} 

Thread *WinPlatform::createThread(const Thread::Action &action) {
   return new WinThread(action);
} 

Platform::Milliseconds WinPlatform::currentTime() {
   timeval currentTime;

   gettimeofday(&currentTime, NULL);

   return currentTime.tv_sec * 1000L + currentTime.tv_usec / 1000L;
}

void WinPlatform::sleep(const Milliseconds time) {
   Sleep(time);
} 

int WinPlatform::htonl(int i) {
   return ::htonl(i);
}

bool isBigEndian() {
   int value = 1;

   // if big endian, value will be stored as 0x00000000000001, so this char
   // will point to 0 (first byte)
   unsigned char *c = (unsigned char *)&value;

   return c == 0;
} 

uint64 reverse(uint64 input) {

   uint64 output = 0;
   
   unsigned char *inPointer = (unsigned char *)&input;
   unsigned char *outPointer = (unsigned char *)&output;

   for (unsigned int i = 0; i < sizeof(uint64); ++i) {
      outPointer[sizeof(uint64) - 1 - i] = inPointer[i];
   } 

   return output;
} 

uint64 WinPlatform::htonll(uint64 i) {
   if (isBigEndian()) return i;

   return reverse(i);
} 

int WinPlatform::ntohl(int i) {
   return ::ntohl(i);
}

uint64 WinPlatform::ntohll(uint64 i) {
   if (isBigEndian()) return i;

   return reverse(i);
}

WinPlatform::~WinPlatform() {
   WSACleanup();
} 
