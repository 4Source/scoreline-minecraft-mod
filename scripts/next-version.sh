#!/usr/bin/env bash
# Prints the next release version, without a leading v, for a patch, minor or major bump.
#
# The version arithmetic lives in tools (NextVersion, unit tested): the highest vX.Y.Z tag is raised by the chosen
# part, and no such tag is an error. This script only reads the tags and checks with git that the result is not in use:
# it fails if the tag or the release branch already exists, locally or on origin.
# Needs a JDK 25 on the PATH: Java's source launcher runs NextVersionLauncher straight from the sources.
set -euo pipefail

# Go to the project root
cd "$(dirname "$0")/.."

if [ $# -ne 1 ]; then
    echo "Usage: scripts/next-version.sh <patch|minor|major>" >&2
    exit 1
fi

launcher=tools/src/main/java/io/github/foursource/scoreline/tools/release/NextVersionLauncher.java

# All v* tags, one argument each; NextVersion ignores those that are not vX.Y.Z
mapfile -t tags < <(git tag --list 'v*')
next=$(java "$launcher" "$1" "${tags[@]}")
# A \r would make the checks below look for a tag that cannot exist
next=${next%$'\r'}

tag="v$next"
branch="release/$tag"
in_use="Release $tag already exists or is in progress"

# Locally
if git rev-parse --quiet --verify "refs/tags/$tag" > /dev/null ||
    git rev-parse --quiet --verify "refs/heads/$branch" > /dev/null; then
    echo "$in_use" >&2
    exit 1
fi

# On origin: exit code 0 = found, 2 = not found, anything else = origin not reachable
set +e
git ls-remote --exit-code origin "refs/tags/$tag" "refs/heads/$branch" > /dev/null
found=$?
set -e
case "$found" in
    0)
        echo "$in_use" >&2
        exit 1
        ;;
    2) ;;
    *)
        echo "Could not check origin for $tag" >&2
        exit 1
        ;;
esac

echo "$next"
