#!/bin/sh
# Builds Thaumaturge from source into libs/, which is the one dependency this project cannot resolve
# from maven.
#
# Thaumaturge is All Rights Reserved. Its LICENSE forbids publishing the mod or any binary built from
# it - section 3.1 names "GitHub Releases on a fork" outright - and it publishes no maven artifact,
# so no build of it can be downloaded, committed, or handed to anyone else. What section 2.4 does
# allow is building it for your own use, which is exactly and only what this script does.
#
# Idempotent: stops if libs/ already holds a Thaumaturge jar. Pass --force to rebuild.
#
#     ./tools/fetch-thaumaturge.sh [--force]
#
# Environment:
#     THAUMATURGE_SRC   where the checkout lives (default <root>/build/thaumaturge-src)
#     JAVA_HOME         must point at a JDK 21
#     CI                if set, Gradle runs without a daemon

set -eu

root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
libs="$root/libs"

commit=$(sed -n 's/^thaumaturge_commit=//p' "$root/gradle.properties" | tr -d '\r')
if [ -z "$commit" ]; then
    echo "fetch-thaumaturge: no thaumaturge_commit line in $root/gradle.properties" >&2
    exit 1
fi

force=0
if [ "${1:-}" = "--force" ]; then
    force=1
fi

existing=$(ls "$libs"/thaumaturge-*.jar 2>/dev/null | head -n1 || true)
if [ "$force" -eq 0 ] && [ -n "$existing" ]; then
    echo "fetch-thaumaturge: already have $existing"
    echo "fetch-thaumaturge: nothing to do (pass --force to rebuild)"
    exit 0
fi

src=${THAUMATURGE_SRC:-"$root/build/thaumaturge-src"}
repo=https://github.com/Leclowndu93150/Thaumaturge.git

if [ ! -d "$src/.git" ]; then
    echo "fetch-thaumaturge: cloning $repo"
    mkdir -p "$(dirname -- "$src")"
    git clone --quiet "$repo" "$src"
fi

echo "fetch-thaumaturge: checking out $commit"
git -C "$src" fetch --quiet origin
git -C "$src" checkout --quiet --force "$commit"

gradle_args=""
if [ -n "${CI:-}" ]; then
    gradle_args="--no-daemon"
fi

echo "fetch-thaumaturge: building Thaumaturge (several minutes the first time)"
if [ -x "$src/gradlew" ]; then
    ( cd "$src" && ./gradlew $gradle_args jar )
else
    ( cd "$src" && sh gradlew $gradle_args jar )
fi

built=$(ls "$src"/build/libs/thaumaturge-*.jar 2>/dev/null | grep -v -- '-sources\.jar$' | grep -v -- '-javadoc\.jar$' | head -n1 || true)
if [ -z "$built" ]; then
    echo "fetch-thaumaturge: the build left no jar in $src/build/libs" >&2
    exit 1
fi

mkdir -p "$libs"
cp -- "$built" "$libs/"
echo "fetch-thaumaturge: $(basename -- "$built") -> $libs/"
echo "fetch-thaumaturge: do not commit this jar or pass it on; the licence forbids both."
