#!/bin/bash

. build.common
. build.win

DLL_FILE_NAME=metatrader-connector.dll

FILES_LIST="$COMMON_FILES
$WIN_FILES \
main.dll.cpp \
main.dll.def"

i686-w64-mingw32-g++ $FILES_LIST $COMMON_OPTIONS $WIN_OPTIONS -o $DLL_FILE_NAME -shared 
