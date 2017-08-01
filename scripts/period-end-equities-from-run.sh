#!/bin/bash

gunzip -c "$1" | grep "period ended with" | sed 's/.*equity: \(.*\), margin.*/\1/'
