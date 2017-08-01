#include "WinThread.h"

WinThread::WinThread(const Action &action)
   : Thread(action) {
   _thread = CreateThread(NULL, 0, (LPTHREAD_START_ROUTINE)callAction, this, 0, NULL);
}

WinThread::~WinThread() {
   WaitForSingleObject(_thread, INFINITE);
   CloseHandle(_thread);
}
