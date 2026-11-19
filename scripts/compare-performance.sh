#!/usr/bin/env bash

# Compares the performance of the current checkout with a baseline.
# Usage: scripts/compare-performance.sh [--allocation-only] [--accepted <file>] <baseline ref>
#   <baseline ref>      a tag, branch or commit; HEAD gives an A/A run
#   --allocation-only   pull request mode: 1 round (ROUNDS overrides), short JMH settings, only allocation compared;
#                       an allocation regression is measured once more and fails only if it repeats
#   --accepted <file>   release mode: regressions listed there (key, tab, reason) are reported as ACCEPTED
# Without --allocation-only: 5 rounds (ROUNDS overrides), 2 forks, time and allocation compared (nightly, release).
#
# Builds the baseline in a git worktree under build/perf/baseline-src and measures both versions with the same ruler:
# the new code's benchmark sources are copied over the baseline's, both benchmark jars are built, and both run with
# one set of JMH options. If the new benchmarks do not compile against the baseline, each version uses its own and the
# table says so. Rounds alternate baseline and new code; results go to build/perf/{baseline,new}/round-<n>/jmh.json.
# Finally :tools:comparePerformance prints the table (also written to build/perf/comparison.md) and fails on a
# regression. A baseline without benchmarks gives an empty baseline folder, which fails unless ALLOW_NO_BASELINE=1.
set -euo pipefail

# Go to the project root
cd "$(dirname "$0")/.."

usage() {
    echo "usage: $0 [--allocation-only] [--accepted <file>] <baseline ref>" >&2
    exit 2
}

allocationOnly=false
accepted=""
while [ $# -gt 1 ]; do
    case "$1" in
        --allocation-only)
            allocationOnly=true
            shift
            ;;
        --accepted)
            accepted=$2
            shift 2
            ;;
        *) usage ;;
    esac
done
[ $# -eq 1 ] || usage
ref=$1

# JMH settings per mode; given on the command line, so both versions run alike whatever their build files say
if $allocationOnly; then
    rounds=${ROUNDS:-1}
    jmhOptions=(-f 1 -wi 3 -i 1 -w 1s -r 1s)
else
    rounds=${ROUNDS:-5}
    jmhOptions=(-f 2 -wi 3 -i 5 -w 1s -r 1s)
fi
jmhOptions+=(-bm avgt -tu ns -prof gc -foe true -rf json)

perf=build/perf
baselineSrc=$perf/baseline-src
# A pattern, expanded later in buildJar
jarGlob='common/build/libs/*-jmh.jar'

# Resolve the ref now, so a typo fails before anything is built
if ! baselineCommit=$(git rev-parse --verify --quiet "$ref^{commit}"); then
    echo "Performance comparison failed: $ref is not a commit"
    exit 2
fi

if [ -n "$(git status --porcelain)" ]; then
    echo "Warning: the checkout has uncommitted changes; they are measured as the new code"
fi

# Removes the baseline worktree, also one left over from an aborted run
removeWorktree() {
    if [ -e "$baselineSrc" ]; then
        git worktree remove --force "$baselineSrc" || rm -rf -- "$baselineSrc"
    fi
    git worktree prune
}

# Start from empty result folders, so results of an earlier run can never be compared
removeWorktree
rm -rf -- "$perf/baseline" "$perf/new" "$perf/recheck" "$perf/comparison.md" "$perf/regressions.txt"
mkdir -p "$perf/baseline" "$perf/new" "$perf/jmh-workdir"

trap removeWorktree EXIT

if ! git worktree add --detach "$baselineSrc" "$baselineCommit"; then
    echo "Performance comparison failed: could not check out $ref into $baselineSrc"
    exit 1
fi

# Builds the benchmark jar of the project in $1 and prints its path
buildJar() {
    local project=$1
    "$project/gradlew" -p "$project" -q :common:jmhJar > /dev/null || return 1
    # The newest, if an earlier version left its jar there too; shellcheck SC2086: the glob must expand
    # shellcheck disable=SC2086,SC2012
    ls -t $project/$jarGlob | head -n 1
}

# A baseline from before the benchmarks existed has no common/src/jmh: its folder stays empty and PerfCompareMain reports "no baseline"
baselineHasBenchmarks=false
fallback=false
if [ -d "$baselineSrc/common/src/jmh" ]; then
    baselineHasBenchmarks=true
    # First with its own benchmarks: if this fails, the build itself is broken (network, toolchain), not the new sources
    baselineJar=$(buildJar "$baselineSrc") || {
        echo "Performance comparison failed: the baseline's own benchmarks do not build"
        exit 1
    }
    # The same ruler: the baseline runs the new code's benchmark sources
    rm -rf -- "$baselineSrc/common/src/jmh"
    cp -r common/src/jmh "$baselineSrc/common/src/jmh"
    if ! baselineJar=$(buildJar "$baselineSrc"); then
        echo "The new benchmarks do not compile against $ref: each version uses its own benchmarks"
        rm -rf -- "$baselineSrc/common/src/jmh"
        git -C "$baselineSrc" checkout -- common/src/jmh
        fallback=true
        baselineJar=$(buildJar "$baselineSrc") || {
            echo "Performance comparison failed: the baseline's own benchmarks do not build"
            exit 1
        }
    fi
else
    echo "Baseline $ref has no benchmarks: only the new code is measured"
fi
newJar=$(buildJar .) || {
    echo "Performance comparison failed: the benchmarks of the new code do not build"
    exit 1
}

# Runs the jar $1 and writes its results to the folder $2; further arguments select benchmarks (JMH include patterns).
# Minecraft's logging writes into the working directory, so the jars run in a scratch folder.
runJar() {
    local jar=$1 target=$2
    shift 2
    mkdir -p "$target"
    local results
    results=$(cd "$perf/jmh-workdir" && pwd)/results.json
    rm -f -- "$results"
    if ! (cd "$perf/jmh-workdir" && java -jar "$OLDPWD/$jar" "${jmhOptions[@]}" -rff "$results" "$@"); then
        echo "Performance comparison failed: the benchmarks of $jar did not run"
        return 1
    fi
    mv -- "$results" "$target/jmh.json"
}

# Alternate baseline and new code in every round, so drift of the machine spreads over both
for round in $(seq 1 "$rounds"); do
    echo "Round $round of $rounds"
    if $baselineHasBenchmarks; then
        runJar "$baselineJar" "$perf/baseline/round-$round"
    fi
    runJar "$newJar" "$perf/new/round-$round"
done

compareArgs=(--regressions "$perf/regressions.txt")
$allocationOnly && compareArgs+=(--allocation-only)
$fallback && compareArgs+=(--fallback)
[ -n "$accepted" ] && compareArgs+=(--accepted "$accepted")

# Runs the comparison with the extra arguments given; prints the table into comparison.md and stdout.
# -q: only the table reaches stdout. Through Gradle both failure codes become 1; the message tells them apart.
compare() {
    local status=0
    ./gradlew -q :tools:comparePerformance --args="${compareArgs[*]} $* $perf/baseline $perf/new" > "$perf/comparison.md" || status=$?
    cat "$perf/comparison.md"
    return "$status"
}

status=0
compare || status=$?

# Pull requests: an allocation regression is measured once more, and only one that repeats fails
if [ "$status" -ne 0 ] && $allocationOnly && $baselineHasBenchmarks && [ -s "$perf/regressions.txt" ]; then
    echo
    echo "Allocation regression: measuring the regressed benchmarks once more"
    includes=()
    while IFS= read -r benchmark; do
        # Anchored on both sides and with literal dots, so XProtocolBenchmark.m does not match ProtocolBenchmark.m
        benchmark=${benchmark%$'\r'}
        includes+=("[.]${benchmark//./[.]}\$")
    done < "$perf/regressions.txt"
    runJar "$baselineJar" "$perf/recheck/baseline/round-1" "${includes[@]}"
    runJar "$newJar" "$perf/recheck/new/round-1" "${includes[@]}"
    status=0
    compare --recheck "$perf/recheck/baseline" "$perf/recheck/new" || status=$?
fi
exit "$status"
