#!/bin/bash
set -em

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

log(){
	echo -e "\e[93m\e[1m[build]\e[0m $*"
}

pushd server >/dev/null
log "Building yggdrasil server"
gradle clean bootJar
popd >/dev/null
pushd test >/dev/null
log "Initialize npm"
npm install .
log "npm test"
npm test
log "Starting yggdrasil server"
../server/build/libs/*.jar "--spring.application.json=$APPLICATION_CONFIG" &
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
