#!/bin/bash

TRAVIS_GRADLE_VERSION="5.2"
TRAVIS_OPENJDK_VERSION="11"
APPLICATION_CONFIG='
{
	"server": {
		"port": 8081
	},
	"yggdrasil": {
		"token": {
			"timeToPartiallyExpired": "2s",
			"timeToFullyExpired": "4s"
		},
		"core": {
			"url": "http://localhost:8081/"
		}
	}
}
'

if tput colors>/dev/null 2>&1;then
	LOG_PREFIX="\e[93m\e[1m[build]\e[0m "
else
	LOG_PREFIX="[build] "
fi
log(){
	echo -e "$LOG_PREFIX$*"
}

set -em
pushd server
if [[ "$1" == "--travis-ci" ]];then
	log "Running on travis-ci"
	log "Creating gradle wrapper $TRAVIS_GRADLE_VERSION"
	mkdir -p .gradle-wrapper
	pushd .gradle-wrapper
	TERM=dumb gradle wrapper --gradle-version $TRAVIS_GRADLE_VERSION
	popd
	cp -r .gradle-wrapper/* ./
	rm -r .gradle-wrapper
	log "Installing OpenJDK $TRAVIS_OPENJDK_VERSION"
	wget https://github.com/sormuras/bach/raw/master/install-jdk.sh
	. ./install-jdk.sh -f $TRAVIS_OPENJDK_VERSION -l GPL -c
	log "Building yggdrasil server"
	TERM=dumb ./gradlew clean bootJar
else
	log "Building yggdrasil server"
	gradle clean bootJar
fi
popd
pushd test
log "Initialize npm"
npm install .
log "npm test"
npm test
log "Starting yggdrasil server"
../server/build/libs/yggdrasil-mock-server-*.jar "--spring.application.json=$APPLICATION_CONFIG" &
onexit(){
	set +e
	if jobs -rp|grep -P "\d+">/dev/null;then
		log "Stopping server"
		pkill -15 -P $(jobs -rp)
		fg 1
	fi
}
trap onexit EXIT

# wait for application to start
while ! lsof -i:8081>/dev/null;do
	if ! jobs -r|grep '\[1\]'>/dev/null;then
		log "Server didn't start"
		exit 1
	fi
	sleep 1
done
log "Server started"

log "Running integration test"
npm --api_root="http://localhost:8081" run integration-test
