#!/usr/bin/env bash
# Prints the next release version, without a leading v, for a patch, minor or major bump.
#
# With no v* tag, the version from gradle.properties is printed unchanged (the first release).
# Otherwise the highest v* tag is raised by the chosen part. Fails if the tag or the release
# branch of the result already exists, locally or on origin.
set -euo pipefail

# Go to the project root
cd "$(dirname "$0")/.."

usage() {
    echo "Usage: scripts/next-version.sh <patch|minor|major>" >&2
    exit 1
}

[ $# -eq 1 ] || usage
bump=$1
case "$bump" in
    patch | minor | major) ;;
    *) usage ;;
esac

# Highest vX.Y.Z tag, compared as version numbers (v0.10.0 ranks above v0.9.0)
latest=$(git tag --list 'v*' --sort=-version:refname | grep -E '^v[0-9]+\.[0-9]+\.[0-9]+$' | head -n 1 || true)

if [ -z "$latest" ]; then
    next=$(grep '^version=' gradle.properties | cut -d= -f2 | tr -d '\r')
else
    IFS=. read -r major minor patch <<< "${latest#v}"
    case "$bump" in
        major) next="$((major + 1)).0.0" ;;
        minor) next="$major.$((minor + 1)).0" ;;
        patch) next="$major.$minor.$((patch + 1))" ;;
    esac
fi

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
