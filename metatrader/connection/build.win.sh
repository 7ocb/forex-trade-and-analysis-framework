#!/bin/bash

. build.common
. build.win

FILES_LIST="$COMMON_FILES
$WIN_FILES \
main.win.cpp"

i686-w64-mingw32-g++ $FILES_LIST $COMMON_OPTIONS $WIN_OPTIONS

