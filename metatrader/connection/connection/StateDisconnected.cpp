#include "StateDisconnected.h"
#include <assert.h>
#include <stdio.h>
#include <functional>

#include "ConnectionHandleListener.h"

StateDisconnected::StateDisconnected(const Context &context)
   : ConnectionState(context) {

}

void StateDisconnected::initState() {
   _context.connectionListener.onDisconnect();
}


StateDisconnected::~StateDisconnected() {

}
