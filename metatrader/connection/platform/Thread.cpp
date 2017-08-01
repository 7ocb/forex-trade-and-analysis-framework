#include "Thread.h"

void Thread::callAction(Thread *self) {
   self->_action();
}

void Thread::joinAndDelete(Thread * &thread) {
   if (thread) {
      delete thread;
      thread = nullptr;
   } 
}

std::string Thread::threadSafeCopy(const std::string& original) {
   return std::string(original.data(),
                      original.size());
} 
