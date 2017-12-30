#!/bin/bash
set -xem
cd server
if [[ "$1" == "--travis" ]];then
	set +x
	source /opt/jdk_switcher/jdk_switcher.sh
	set -x
	export GRADLE_OPTS="-Dorg.gradle.console=plain"
	jdk_switcher use oraclejdk8
	gradle wrapper --gradle-version 4.4.1
	jdk_switcher use oraclejdk9
	./gradlew clean bootJar
else
	gradle clean bootJar
fi
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
