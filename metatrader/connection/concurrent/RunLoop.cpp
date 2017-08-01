#include "RunLoop.h"
#include <algorithm>
#include <stdio.h>

class RunLoop::TaskInternal : public RunLoop::Task {
public:
   TaskInternal(const Platform::Milliseconds targetTime) : _targetTime(targetTime) {}

   Platform::Milliseconds targetTime() const { return _targetTime; }

   virtual bool isTerminate() const = 0;
   virtual void run() = 0;
      
private:
   const Platform::Milliseconds _targetTime;
};
   
class RunLoop::ActionTask : public RunLoop::TaskInternal {
   void operator=(const ActionTask& );
   ActionTask(const ActionTask& task);      

public:
   ActionTask(const Platform::Milliseconds targetTime,
              const Action& action)
      : TaskInternal(targetTime)
      , _action(action) {
         
   } 

   bool isTerminate() const { return false; }
         
   void run() { _action(); } 
      
private:
   const Action _action;
};

class RunLoop::TerminateTask : public RunLoop::TaskInternal {
   void operator=(const TerminateTask& );
   TerminateTask(const TerminateTask& task);      

public:
   TerminateTask(const Platform::Milliseconds targetTime) : TaskInternal(targetTime) {}

   bool isTerminate() const { return true; }
         
   void run() {}

};

RunLoop::RunLoop()
   : _taskListMonitor(Platform::instance().createMonitor()) {
   
} 

RunLoop::Task *RunLoop::post(const Action& action) {
   return postDelayed(0,
                      action);
} 

bool RunLoop::isFirstTaskEarlier(TaskInternal *first,
                                 TaskInternal *second) {
   return first->targetTime() < second->targetTime();
} 

RunLoop::Task *RunLoop::postDelayed(Platform::Milliseconds delay,
                                    const Action& action) {
   const Platform::Milliseconds currentTime = Platform::instance().currentTime();

   const Platform::Milliseconds targetTime = currentTime + delay;
   
   ActionTask *task = new ActionTask(targetTime,
                                     action);

   return putToQueue(task);
}

RunLoop::Task *RunLoop::putToQueue(TaskInternal *task) {
   _taskListMonitor->lock();
   
   _tasks.push_back(task);
   
   _tasks.sort(isFirstTaskEarlier);

   // if (_tasks.size() > 4000) {
   //    printf("queue size: %d", _tasks.size());
   // }

   _taskListMonitor->notify();
   _taskListMonitor->unlock();
   
   return task;   
} 

void RunLoop::cancel(Task *task) {
   // find task in tasks queue:
   _taskListMonitor->lock();

   auto found = std::find(_tasks.begin(), _tasks.end(), task);

   if (found != _tasks.end()) {
      delete task;
      _tasks.erase(found);
   }

   _taskListMonitor->notify();
   
   _taskListMonitor->unlock();
} 

RunLoop::TaskInternal *RunLoop::popNextTask() {

   TaskInternal *result = NULL;
   
   _taskListMonitor->lock();

   while (true) {

      // printf("tasks size: %lu, empty: %d\n", _tasks.size(), _tasks.empty());
      
      if ( _tasks.empty() ) {
         // printf("entering wait\n");
         _taskListMonitor->wait();
         // printf("leaved wait\n");
      } else {
         // printf("processing first\n");

         TaskInternal * const candidate = *_tasks.begin();

         const Platform::Milliseconds currentTime = Platform::instance().currentTime();

         // printf("current time: %llu, target time: %llu\n", currentTime, candidate->targetTime());
         
         if (candidate->targetTime() < currentTime) {
            // printf("returning first\n");
            result = candidate;
            _tasks.pop_front();

            break;
         } else {
            // printf("entering timed wait\n");
            _taskListMonitor->wait(candidate->targetTime() - currentTime);
            // printf("leaved timed wait\n");
         } 
      } 
   }
   
   _taskListMonitor->unlock();

   return result;
} 

void RunLoop::run() {

   bool work = true;
   
   while (work) {
      TaskInternal *nextTask = popNextTask();

      if (nextTask->isTerminate()) {
         work = false;
      } else {
         nextTask->run();
      } 

      delete nextTask;
   } 
}

void RunLoop::deleteAllTasks() {
   for (auto i = _tasks.begin(); i != _tasks.end(); ++i) {

      delete *i;
   }

   _tasks.clear();
} 

void RunLoop::terminate() {
   // delete all tasks

   _taskListMonitor->lock();

   deleteAllTasks();

   _tasks.push_back(new TerminateTask(0));

   _taskListMonitor->notify();
   
   _taskListMonitor->unlock();
}

RunLoop::~RunLoop() {
   deleteAllTasks();

   delete _taskListMonitor;
} 
