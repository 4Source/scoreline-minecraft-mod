#!/usr/bin/env bash
# Smoke test: a dedicated NeoForge server starts with the built jar, logs Scoreline's version and stops cleanly.
# Run ./gradlew build first. The server is installed once per NeoForge version into build/smoke-server; each run 
# starts from an empty world, mods and logs folder.
# SMOKE_START_SECONDS (default 300) bounds the start, SMOKE_STOP_SECONDS (default 60) the stop.
set -euo pipefail

# Go to the project root
cd "$(dirname "$0")/.."

version=$(grep '^version=' gradle.properties | cut -d= -f2)
neoforge=$(grep '^neoforge_version=' gradle.properties | cut -d= -f2)
server=build/smoke-server
console=$server/console.log
startSeconds=${SMOKE_START_SECONDS:-300}
stopSeconds=${SMOKE_STOP_SECONDS:-60}

# Prints why the test failed and the end of the server's console, then exits 1
fail() {
    echo "Smoke test failed: $1"
    if [ -f "$console" ]; then
        echo "--- last lines of $console"
        tail -n 40 "$console"
    fi
    exit 1
}

# 1. Install the dedicated server of this NeoForge version once
if [ ! -f "$server/.installed-$neoforge" ]; then
    rm -rf -- "$server"
    mkdir -p "$server"
    installer=neoforge-$neoforge-installer.jar
    curl -fsSL -o "$server/$installer" "https://maven.neoforged.net/releases/net/neoforged/neoforge/$neoforge/$installer" ||
        fail "could not download the NeoForge installer $neoforge"
    (cd "$server" && java -jar "$installer" --installServer .) > "$server/install.log" 2>&1 ||
        fail "the NeoForge installer failed, see $server/install.log"
    touch "$server/.installed-$neoforge"
fi

# 2. The built jar, and a fresh world
jar=$(find neoforge/build/libs -maxdepth 1 -name "scoreline-$version+*-neoforge.jar" | head -n 1)
[ -n "$jar" ] || fail "no built jar for version $version in neoforge/build/libs; run ./gradlew build first"
rm -rf -- "$server/mods" "$server/world" "$server/logs" "$console"
mkdir -p "$server/mods"
cp -- "$jar" "$server/mods/"
echo "eula=true" > "$server/eula.txt"
cat > "$server/server.properties" << 'EOF'
online-mode=false
server-port=25599
level-type=minecraft\:flat
generate-structures=false
spawn-protection=0
max-players=1
EOF

# The installer writes the server's arguments into files; the Windows one uses ';' in the class path
case "$(uname -s)" in
    MINGW* | MSYS* | CYGWIN*) argsFile=win_args.txt ;;
    *) argsFile=unix_args.txt ;;
esac

# 3. Start the server as a coprocess, so this script can write to its console and watch its log. The whole run is
# bounded, so a server that hangs on start or stop fails the test instead of the job's time limit.
coproc SERVER { cd "$server" && exec timeout "$((startSeconds + stopSeconds))" java @user_jvm_args.txt "@libraries/net/neoforged/neoforge/$neoforge/$argsFile" nogui > console.log 2>&1; }
serverPid=$SERVER_PID
outcome=timeout
for ((waited = 0; waited < startSeconds; waited++)); do
    if grep -q 'Done (' "$console" 2> /dev/null; then
        outcome=ready
        break
    fi
    # A mod that fails to load leaves the server running without starting it: end it at once
    if grep -q -e 'Failed to start the minecraft server' -e 'ModLoadingException' "$console" 2> /dev/null; then
        outcome=failed
        break
    fi
    kill -0 "$serverPid" 2> /dev/null || {
        outcome=exited
        break
    }
    sleep 1
done
if [ "$outcome" = ready ]; then
    echo stop >&"${SERVER[1]}"
elif kill -0 "$serverPid" 2> /dev/null; then
    kill "$serverPid" 2> /dev/null || true
fi
status=0
wait "$serverPid" || status=$?

# 4. The verdict
case "$outcome" in
    failed) fail "Scoreline $version did not load: the server failed to start" ;;
    exited) fail "the server exited before it was ready" ;;
    timeout) fail "the server was not ready within $startSeconds s" ;;
esac
grep -qF "Scoreline $version loaded" "$console" || fail "Scoreline $version did not load"
[ "$status" -eq 0 ] || fail "the server did not stop cleanly (exit code $status)"
echo "Smoke test passed: Scoreline $version loaded on a dedicated NeoForge $neoforge server, which stopped cleanly"
