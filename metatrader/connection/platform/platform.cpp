#include <cstddef>
#include "platform.h"

Platform * Platform::_instance;

Platform &Platform::instance() {
   return *_instance;
} 

void Platform::init(Platform *instance) {
   _instance = instance;
}

void Platform::cleanup() {
   if (_instance) {
      delete _instance;
      _instance = NULL;
   } 
}

