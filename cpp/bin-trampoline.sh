#!/bin/bash

base_dir=$(dirname $0)
base_name=$(basename $0)
bin_dir=$base_dir/build-`uname -s | tr '[A-Z]' '[a-z]'`-`uname -m`

binary=$bin_dir/$base_name

if [ ! -x $binary ]; then
    echo "ERROR: Binary executable $binary is missing!"
    echo "Compile it using scripts/build-binaries."
    exit 1
fi

exec $binary $*
