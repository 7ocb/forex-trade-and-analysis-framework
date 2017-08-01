#ifndef __66A6CEC32FB740A46585CF2BE40900CD_RUNLOOP_H_INCLUDED__
#define __66A6CEC32FB740A46585CF2BE40900CD_RUNLOOP_H_INCLUDED__

#include <list>
#include <cstddef>
#include "platform.h"


class RunLoop {

   
public:

   typedef std::function<void()> Action;
   
   // task is deleted internally by run loop, either when executed or
   // cancelled
   class Task {
   public:
      virtual ~Task() {};
   };

private:

   class TaskInternal;
   class ActionTask;
   class TerminateTask;

   // can't be copied
   RunLoop(const RunLoop&);
   void operator=(const RunLoop&);

public:

   RunLoop();

   Task *post(const Action &action);
   
   Task *postDelayed(Platform::Milliseconds delay,
                     const Action &action);

   void cancel(Task *task);
   
   void run();
   void terminate();

   void deleteAllTasks();

   virtual ~RunLoop();

private:

   Task *putToQueue(TaskInternal *task);
   
   TaskInternal *popNextTask();

   static bool isFirstTaskEarlier(TaskInternal *first,
                                  TaskInternal *second);
   
private:
   
   std::list<TaskInternal *> _tasks;

   Monitor *_taskListMonitor;
};

#endif 	// __66A6CEC32FB740A46585CF2BE40900CD_RUNLOOP_H_INCLUDED__
