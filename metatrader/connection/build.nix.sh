
#!/bin/bash

. build.common

FILES_LIST="$COMMON_FILES
main.nix.cpp \
platform/nix/NixPlatform.h \
platform/nix/NixPlatform.cpp \
platform/nix/NixMonitor.h \
platform/nix/NixMonitor.cpp \
platform/nix/NixThread.h \
platform/nix/NixThread.cpp \
platform/nix/NixSocket.h \
platform/nix/NixSocket.cpp"


g++ -g  $FILES_LIST -lpthread -Iplatform/nix $COMMON_OPTIONS
