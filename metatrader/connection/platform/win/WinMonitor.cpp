#include "WinMonitor.h"
#include <assert.h>

WinMonitor::WinMonitor() {

} 

void WinMonitor::lock() {
   _monitor.BeginSynchronized();
}

void WinMonitor::unlock() {
   _monitor.EndSynchronized();
} 

void WinMonitor::wait() {
   _monitor.Wait();
}

void WinMonitor::wait(uint64 milliseconds) {
   _monitor.Wait(milliseconds);
}

void WinMonitor::notify() {
   _monitor.Notify();
} 
   
WinMonitor::~WinMonitor() {
   
} 
