#!/usr/bin/env bash
set -euo pipefail

# Go to the project root
cd "$(dirname "$0")/.."

goldenBuilds=./common/build/goldens/
goldenResources=./common/src/test/resources/protocol/

# A version folder that any release contains never changes; releasedIn names that release
releasedIn=""
released() {
    local tag
    for tag in $(git tag --list 'v*'); do
        if git cat-file -e "$tag:common/src/test/resources/protocol/$1" 2> /dev/null; then
            releasedIn=$tag
            return 0
        fi
    done
    return 1
}

# Delete the last generated goldens if they exist, so an old golden will never be copied
rm -r -f -- "$goldenBuilds"
if [ -e "$goldenBuilds" ]; then
    echo "Generate goldens run failed: $goldenBuilds could not be deleted"
    exit 1
fi

# Write the goldens of the current protocol version to $goldenBuilds
if ! ./gradlew :common:generateGoldens; then
    echo "Generate goldens run failed: the Gradle task exited non successful"
    exit 1
fi
if [ -z "$(find "$goldenBuilds" -type f -name '*.bin' 2> /dev/null)" ]; then
    echo "Generate goldens run failed: no golden files were written to $goldenBuilds"
    exit 1
fi

# Asks a yes/no question; succeeds only on y or Y.
# Reads the answer from the terminal, not from stdin, so the prompt also works when the script is piped.
# Without a terminal (for example in CI) /dev/tty cannot be opened and the answer is no.
ask() {
    local answer=n
    if (: < /dev/tty) 2> /dev/null; then
        read -r -p "$1 [y/N] " answer < /dev/tty || answer=n
    fi
    [ "$answer" = y ] || [ "$answer" = Y ]
}

# Counts the generated files that were not saved: a new version or new file declined, or differing bytes kept
notSaved=0

# Go through each generated version folder (v1, v2, ...), sorted so the output is always in the same order
while IFS= read -r -d '' versionDir; do
    version=$(basename -- "$versionDir")
    files=$(find "$versionDir" -type f -name '*.bin' | wc -l)

    # Released: nothing is written. Identical bytes are fine; anything else is refused and fails the run.
    if released "$version"; then
        while IFS= read -r -d '' generated; do
            relative=${generated#"$goldenBuilds"}
            if [ -e "$goldenResources$relative" ] && cmp -s -- "$generated" "$goldenResources$relative"; then
                echo "unchanged $relative"
            else
                echo "refused   $relative: $version is part of the release $releasedIn, so its example messages never change"
                notSaved=$((notSaved + 1))
            fi
        done < <(find "$versionDir" -type f -name '*.bin' -print0 | sort -z)
        continue
    fi

    # A version without a resources folder is new. Raising Protocol.VERSION by accident would create it, so ask first.
    newVersion=false
    if [ ! -d "$goldenResources$version" ]; then
        newVersion=true
        echo "new version $version ($files file(s)):"
        find "$versionDir" -type f -name '*.bin' -print0 | sort -z | while IFS= read -r -d '' generated; do
            echo "  ${generated#"$versionDir/"}"
            xxd "$generated" | sed 's/^/    /'
        done
        if ! ask "Save the goldens of the new protocol version $version?"; then
            echo "skipped   $version"
            notSaved=$((notSaved + files))
            continue
        fi
    fi

    # Copy each generated file of this version to the resources
    while IFS= read -r -d '' generated; do
        # The path below the goldens folder, for example v1/hello.bin
        relative=${generated#"$goldenBuilds"}
        target=$goldenResources$relative

        if [ ! -e "$target" ]; then
            # A new golden in an existing version: a released version's messages must not grow by accident, so show the bytes and ask.
            # Files of a new version were already shown and confirmed as a whole above.
            if [ "$newVersion" = false ]; then
                echo "new file  $relative"
                xxd "$generated" | sed 's/^/    /'
                if ! ask "Add $relative to the existing version $version?"; then
                    echo "skipped   $relative"
                    notSaved=$((notSaved + 1))
                    continue
                fi
            fi
            mkdir -p -- "$(dirname -- "$target")"
            cp -- "$generated" "$target"
            echo "created   $relative"
        elif cmp -s -- "$generated" "$target"; then
            # Same bytes: nothing to do
            echo "unchanged $relative"
        else
            # Different bytes: a golden must not change by accident, so show the difference and ask
            echo "changed   $relative (< committed, > generated)"
            diff <(xxd "$target") <(xxd "$generated")

            if ask "Overwrite $relative?"; then
                cp -- "$generated" "$target"
                echo "replaced  $relative"
            else
                echo "kept      $relative"
                notSaved=$((notSaved + 1))
            fi
        fi
    done < <(find "$versionDir" -type f -name '*.bin' -print0 | sort -z)
done < <(find "$goldenBuilds" -mindepth 1 -maxdepth 1 -type d -print0 | sort -z)

# Fail if a generated golden was not saved, so a mismatch never passes unnoticed
if [ "$notSaved" -gt 0 ]; then
    echo "Generate goldens run failed: $notSaved generated golden file(s) were not saved"
    exit 1
fi
