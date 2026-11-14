#!/usr/bin/env bash
# Exits with code 0 if gradle.properties contains version=<version>, else with code 1.
set -euo pipefail

# Go to the project root
cd "$(dirname "$0")/.."

if [ $# -ne 1 ]; then
    echo "Usage: scripts/check-version.sh <version>" >&2
    exit 1
fi

expected=$1
actual=$(grep '^version=' gradle.properties | cut -d= -f2 | tr -d '\r')

if [ "$actual" != "$expected" ]; then
    echo "Version mismatch: expected $expected, found $actual" >&2
    exit 1
fi
