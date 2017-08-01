#include "StateConnectFailed.h"
#include <assert.h>
#include <stdio.h>
#include <functional>

#include "ConnectionHandleListener.h"

StateConnectFailed::StateConnectFailed(const Context &context)
   : ConnectionState(context) {

}

void StateConnectFailed::initState() {
   _context.connectionListener.onConnectFailed();
}


StateConnectFailed::~StateConnectFailed() {

}
