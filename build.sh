#!/bin/bash
set -xem
cd server
gradle wrapper --gradle-version 4.4.1
./gradlew clean bootJar
cd ../test
npm install .
npm test
../server/build/libs/yggdrasil-mock-server-*.jar &
onexit(){
	set +e
	pkill -15 -P $(jobs -rp)
	fg 1
}
trap onexit EXIT
sleep 10 # wait for application to start
if jobs -r|grep '\[1\]'>/dev/null;then
	npm run integration-test
else
	exit 1
fi
