#!/usr/bin/env bash

if [ -z "${SERVER_URL}" ]; then
  echo "SERVER_URL is not set!"
fi

tmpDir=./target


# remove temporary directory and re-create it
rm -rf "${tmpDir}" && mkdir -p "${tmpDir}/src"

# copy project files to temporary directory
cp -r concord.yml "${tmpDir}/"
rsync --recursive src/main "${tmpDir}/src/"

cd ${tmpDir} && zip -r payload.zip ./* && cd ..  2>&1>/dev/null

curl -n \
  -F archive=@target/payload.zip \
  ${ENTRY_POINT:+ -F "entryPoint=$ENTRY_POINT"} \
  ${ORG:+ -F "org=$ORG"} \
  ${PROJECT:+ -F "project=$PROJECT"} \
  ${ACTIVE_PROFILES:+ -F "activeProfiles=$ACTIVE_PROFILES"} \
 " ${SERVER_URL}/api/v1/process"
