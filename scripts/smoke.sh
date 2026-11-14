#!/usr/bin/env bash

# Go to the projects root
cd "$(dirname "$0")/.."

log=./neoforge/runs/gametest/logs/latest.log

# Read the version from gradle.properties
version=$(grep '^version=' gradle.properties | cut -d= -f2)

# Delete the latest log if it exists, so an old log can never pass the check
rm -f -- "$log"
if [ -e "$log" ]; then
    echo "Smoke run failed: $log could not be deleted"
    exit 1
fi

# Pass only if the server run succeeded
if ./gradlew :neoforge:runGameTestServer; then
    # Pass only if the log shows Scoreline's start line
    if grep -qF "Scoreline $version loaded" "$log"; then
        exit 0
    else
        echo "Smoke run failed: Scoreline did not load"
        exit 1
    fi
else 
    echo "Smoke run failed: Server run exited non successful"
    exit 1
fi
