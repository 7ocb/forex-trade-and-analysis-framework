#include "NixMonitor.h"
#include <assert.h>

NixMonitor::NixMonitor() {

   pthread_mutexattr_t mutexAttributes;
   pthread_mutexattr_init(&mutexAttributes);

   pthread_mutexattr_settype(&mutexAttributes,
                             PTHREAD_MUTEX_RECURSIVE);
   
   pthread_mutex_init(&_mutex, &mutexAttributes);
   pthread_cond_init(&_condition, NULL);
} 

void NixMonitor::lock() {
   pthread_mutex_lock(&_mutex);

}

void NixMonitor::unlock() {

   pthread_mutex_unlock(&_mutex);
} 

void NixMonitor::wait() {

   pthread_cond_wait(&_condition,
                     &_mutex);
}

void NixMonitor::wait(uint64 milliseconds) {
   timespec targetTime;
   if (clock_gettime(CLOCK_REALTIME, &targetTime)) {
      printf("getting time error: %d\n", errno);
   } 

   targetTime.tv_sec += milliseconds / 1000;
   targetTime.tv_nsec += (milliseconds % 1000) * 1000000L;

   while (targetTime.tv_nsec > 999999999L) {
      targetTime.tv_sec += 1;
      targetTime.tv_nsec -= 1000000000L;
   } 
   
   pthread_cond_timedwait(&_condition,
                          &_mutex,
                          &targetTime);

}

void NixMonitor::notify() {
   pthread_cond_signal(&_condition);
} 
   
NixMonitor::~NixMonitor() {
   pthread_cond_destroy(&_condition);
   pthread_mutex_destroy(&_mutex);
} 
