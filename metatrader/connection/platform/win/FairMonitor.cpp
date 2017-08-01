/**********************************************************************
*
COPYRIGHT (C) 2000 Thomas Becker

PERMISSION NOTICE:

PERMISSION TO USE, COPY, MODIFY, AND DISTRIBUTE THIS SOFTWARE FOR ANY
PURPOSE AND WITHOUT FEE IS HEREBY GRANTED, PROVIDED THAT ALL COPIES
ARE ACCOMPANIED BY THE COMPLETE MACHINE-READABLE SOURCE CODE, ALL
MODIFIED FILES CARRY PROMINENT NOTICES AS TO WHEN AND BY WHOM THEY
WERE MODIFIED, THE ABOVE COPYRIGHT NOTICE, THIS PERMISSION NOTICE AND
THE NO-WARRANTY NOTICE BELOW APPEAR IN ALL SOURCE FILES AND IN ALL
SUPPORTING DOCUMENTATION.

NO-WARRANTY NOTICE:

THIS SOFTWARE IS PROVIDED "AS IS" WITHOUT EXPRESS OR IMPLIED WARRANTY.
ALL IMPLIED WARRANTIES OF FITNESS FOR ANY PARTICULAR PURPOSE AND OF
MERCHANTABILITY ARE HEREBY DISCLAIMED.
*
**********************************************************************/
 
//
// File: FairMonitor.cpp
//
// Description: Implementation of class FairMonitor
//
// Revision History:
// 
// Thomas Becker Dec 2000: Created
// Thomas Becker Oct 2006: More error checking in Wait() and EndSynchronized()
//

#include"assert.h"
#include<algorithm>
#include "FairMonitor.h"

///////////////////////////////////////////////////////////////////////
//
FairMonitor::FairMonitor() :
  m_nLockCount(0)
{
  // Initialize the critical section that protects access to
  // the wait set. There is no way to check for errors here.
  ::InitializeCriticalSection(&m_critsecWaitSetProtection);

  // Initialize the critical section that provides synchronization
  // to the client. There is no way to check for errors here.
  ::InitializeCriticalSection(&m_critsecSynchronized);
}

///////////////////////////////////////////////////////////////////////
//
FairMonitor::~FairMonitor()
{
  // Uninitialize critical sections. Win32 allows no error checking
  // here.
  ::DeleteCriticalSection(&m_critsecWaitSetProtection);
  ::DeleteCriticalSection(&m_critsecSynchronized);

  // Destroying this thing while threads are waiting is a client
  // programmer mistake.
  assert( m_deqWaitSet.empty() );
}

///////////////////////////////////////////////////////////////////////
//
void FairMonitor::BeginSynchronized()
{ 
  // Acquire lock. Win32 allows no error checking here.
  ::EnterCriticalSection(&m_critsecSynchronized); 

  // Record the lock acquisition for proper release in Wait().
  ++m_nLockCount;
}

///////////////////////////////////////////////////////////////////////
//
BOOL FairMonitor::EndSynchronized()
{ 
  if( ! LockHeldByCallingThread() )
  {
    ::SetLastError(ERROR_INVALID_FUNCTION); // for the lack of better...
    return FALSE;
  }

  // Record the lock release for proper release in Wait().
  --m_nLockCount;

  // Release lock. Win32 allows no error checking here.
  ::LeaveCriticalSection(&m_critsecSynchronized); 
  
  return TRUE;
}

///////////////////////////////////////////////////////////////////////
//
DWORD FairMonitor::Wait(DWORD dwMillisecondsTimeout/* = INFINITE*/)
{
  if( ! LockHeldByCallingThread() )
  {
    ::SetLastError(ERROR_INVALID_FUNCTION); // for the lack of better...
    return WAIT_FAILED;
  }

  if(dwMillisecondsTimeout == 0) 
  {
    return WAIT_TIMEOUT;
  }

  // Enter a new event handle into the wait set.
  HANDLE hWaitEvent = Push();
  if( NULL == hWaitEvent )
    return WAIT_FAILED;

  // Store the current lock count for re-acquisition.
  int nThisThreadsLockCount = m_nLockCount;
  m_nLockCount = 0;

  // Release the synchronization lock the appropriate number of times.
  // Win32 allows no error checking here.
  for( int i=0; i<nThisThreadsLockCount; ++i)
    ::LeaveCriticalSection(&m_critsecSynchronized);

  // NOTE: Conceptually, releasing the lock and entering the wait
  // state is done in one atomic step. Technically, that is not
  // true here, because we first leave the critical section and
  // then, in a separate line of code, call WaitForSingleObjectEx.
  // The reason why this code is correct is that our thread is placed
  // in the wait set *before* the lock is released. Therefore, if
  // we get preempted right here and another thread notifies us, then
  // that notification will *not* be missed: the wait operation below
  // will find the event signalled.
  
  // Wait for the event to become signalled.
  DWORD dwWaitResult = ::WaitForSingleObject(
    hWaitEvent,
    dwMillisecondsTimeout
    );

  // If the wait failed, store the last error because it will get
  // overwritten when acquiring the lock.
  DWORD dwLastError;
  if( WAIT_FAILED == dwWaitResult )
    dwLastError = ::GetLastError();

  // In case of a timeout, we are responsible for erasing the
  // handle from the wait set.
  //
  if( WAIT_TIMEOUT == dwWaitResult )
  {
    // It is possible that a notification has happened since
    // the timeout occurred. In that case, we don't find our handle
    // in the wait set anymore. We must then act as if the wait
    // completed successfully rather than timing out. (Actually,
    // if it was a NotifyAll() that nixed our handle, we could
    // continue with the timeout. The critical case is where it
    // was a Notify(), and there were other handles in the wait
    // set. In that case, we *must* forget about our timeout, or
    // else we'd have a missed signal.)
    //
    if(! Erase(hWaitEvent))
    {
      dwWaitResult = WAIT_OBJECT_0;
    }
  }

  // Acquire the synchronization lock the appropriate number of times.
  // Win32 allows no error checking here.
  for( int j=0; j<nThisThreadsLockCount; ++j)
    ::EnterCriticalSection(&m_critsecSynchronized);

  // Restore lock count.
  m_nLockCount = nThisThreadsLockCount;

  // Close event handle
  if( ! CloseHandle(hWaitEvent) )
    return WAIT_FAILED;

  if( WAIT_FAILED == dwWaitResult )
    ::SetLastError(dwLastError);

  return dwWaitResult;
}

///////////////////////////////////////////////////////////////////////
//
BOOL FairMonitor::Notify()
{
  if( ! LockHeldByCallingThread() )
  {
    ::SetLastError(ERROR_INVALID_FUNCTION); // for the lack of better...
    return FALSE;
  }

  // Pop the first handle, if any, off the wait set.
  HANDLE hWaitEvent = Pop();

  // If there is not thread currently waiting, that's just fine.
  if(NULL == hWaitEvent)
    return TRUE;

  // Signal the event.
  return SetEvent(hWaitEvent);
}

///////////////////////////////////////////////////////////////////////
//
BOOL FairMonitor::NotifyAll()
{
  if( ! LockHeldByCallingThread() )
  {
    ::SetLastError(ERROR_INVALID_FUNCTION); // for the lack of better...
    return FALSE;
  }

  // Signal all events on the deque, then clear it. Win32 allows no
  // error checking on entering and leaving the critical section.
  //
  ::EnterCriticalSection(&m_critsecWaitSetProtection);
  std::deque<HANDLE>::const_iterator it_run = m_deqWaitSet.begin();
  std::deque<HANDLE>::const_iterator it_end = m_deqWaitSet.end();
  for( ; it_run < it_end; ++it_run )
  {
    if( ! SetEvent(*it_run) )
      return FALSE;
  }
  m_deqWaitSet.clear();
  ::LeaveCriticalSection(&m_critsecWaitSetProtection);
  
  return TRUE;
}

///////////////////////////////////////////////////////////////////////
//
HANDLE FairMonitor::Push()
{
  // Create the new event.
  HANDLE hWaitEvent = ::CreateEvent(
    NULL, // no security
    FALSE, // auto-reset event
    FALSE, // initially unsignalled
    NULL // string name
    );
  //
  if( NULL == hWaitEvent ) {
    return NULL;
  }

  // Push the handle on the deque.
  ::EnterCriticalSection(&m_critsecWaitSetProtection);
  m_deqWaitSet.push_back(hWaitEvent);
  ::LeaveCriticalSection(&m_critsecWaitSetProtection);

  return hWaitEvent;
}

///////////////////////////////////////////////////////////////////////
//
HANDLE FairMonitor::Pop()
{
  // Pop the first handle off the deque.
  //
  ::EnterCriticalSection(&m_critsecWaitSetProtection);
  HANDLE hWaitEvent = NULL; 
  if( 0 != m_deqWaitSet.size() )
  {
    hWaitEvent = m_deqWaitSet.front();
    m_deqWaitSet.pop_front();
  }
  ::LeaveCriticalSection(&m_critsecWaitSetProtection);

  return hWaitEvent;
}

///////////////////////////////////////////////////////////////////////
//
BOOL FairMonitor::Erase(HANDLE hWaitEvent)
{
  ::EnterCriticalSection(&m_critsecWaitSetProtection);

  // Find the handle in the deque
  //
  std::deque<HANDLE>::iterator handlePos = std::find(m_deqWaitSet.begin(), m_deqWaitSet.end(), hWaitEvent);
  BOOL retVal = FALSE;
  if(handlePos != m_deqWaitSet.end())
  {
    m_deqWaitSet.erase(handlePos);
    retVal = TRUE;
  }
  ::LeaveCriticalSection(&m_critsecWaitSetProtection);
  
  return retVal;
}

///////////////////////////////////////////////////////////////////////
//
BOOL FairMonitor::LockHeldByCallingThread()
{
  BOOL bTryLockResult = ::TryEnterCriticalSection(&m_critsecSynchronized);

  // If we didn't get the lock, someone else has it.
  //
  if( ! bTryLockResult )
  {
    return FALSE;
  }

  // If we got the lock, but the lock count is zero, then nobody had it.
  //
  if( 0 == m_nLockCount )
  {
    assert( bTryLockResult );
    ::LeaveCriticalSection(&m_critsecSynchronized);
    return FALSE;
  }

  // Release lock once. NOTE: we still have it after this release.
  // Win32 allows no error checking here.
  assert( bTryLockResult && 0 < m_nLockCount );
  ::LeaveCriticalSection(&m_critsecSynchronized);
 
  return TRUE;
}
