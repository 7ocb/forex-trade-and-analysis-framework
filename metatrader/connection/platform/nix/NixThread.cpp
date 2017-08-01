#include "NixThread.h"

NixThread::NixThread(const Action &action)
   : Thread(action) {

   pthread_attr_init(&_attr);
   pthread_attr_setdetachstate(&_attr, PTHREAD_CREATE_JOINABLE);
   
   pthread_create(&_thread,
                  &_attr,
                  (void *(*)(void *))callAction,
                  this);
}

NixThread::~NixThread() {
   pthread_join(_thread, NULL);
   pthread_attr_destroy(&_attr);
} 
