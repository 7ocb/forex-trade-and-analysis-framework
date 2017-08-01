#!/bin/bash

marker="End Equity"

find -name '*.stats' -exec grep -H "$marker" {} ";" | sed "s/\(.*\):$marker: \(.*\)/\2 - \1/" | sort -n
