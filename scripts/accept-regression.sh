#!/usr/bin/env bash
# Accepts an intended performance regression: appends "<metric key><TAB><reason>" to perf-accepted.txt.
# Usage: scripts/accept-regression.sh '<metric key>' '<reason>'
#   <metric key>  as the comparison table shows it, for example 'ProtocolBenchmark.encodeHello (time)'
#   <reason>      why the slowdown is intended; the release notes quote it
# The pull request, nightly and release comparisons then report that metric as ACCEPTED instead of failing.
# Exit codes: 0 when added, 1 when the key is already listed, 2 on wrong input.
set -euo pipefail

# Go to the project root
cd "$(dirname "$0")/.."

file=perf-accepted.txt
comparison=build/perf/comparison.md

if [ $# -ne 2 ]; then
    echo "Usage: scripts/accept-regression.sh '<metric key>' '<reason>'" >&2
    exit 2
fi
key=$1
reason=$2

# The key ends with its kind, exactly as PerfCompare writes it; a tab or a line break would break the file's format
if [[ ! "$key" =~ ^[^[:cntrl:]]+\ \((time|allocation)\)$ ]]; then
    echo "Not a metric key: '$key'. It ends with ' (time)' or ' (allocation)', as in the comparison table" >&2
    exit 2
fi
if [[ -z "${reason//[[:space:]]/}" || "$reason" =~ [[:cntrl:]] ]]; then
    echo "The reason must not be empty and must be a single line without tabs" >&2
    exit 2
fi

if cut -f1 "$file" | grep -qxF -- "$key"; then
    echo "$key is already accepted in $file" >&2
    exit 1
fi

# The last comparison run shows which keys exist; a key it does not list may be a typo
if [ -f "$comparison" ] && ! grep -qF -- "| $key |" "$comparison"; then
    echo "Warning: $key is not in the last comparison ($comparison); check the spelling" >&2
fi

# Starts a new line if the file does not end with one, so the entry never joins the previous line
if [ -s "$file" ] && [ -n "$(tail -c 1 "$file")" ]; then
    echo >> "$file"
fi
printf '%s\t%s\n' "$key" "$reason" >> "$file"
echo "Accepted in $file: $key"
