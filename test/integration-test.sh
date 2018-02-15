#!/bin/bash
if [ "$1" != "" ];then
	export API_ROOT="$1"
fi
mocha src/test --timeout 10000
