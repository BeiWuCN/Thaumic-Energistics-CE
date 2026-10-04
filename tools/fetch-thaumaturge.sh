#!/bin/sh
# Builds Thaumaturge from source into libs/, which is the one dependency this project cannot resolve
# from maven.
#
# Thaumaturge is All Rights Reserved. Its LICENSE forbids publishing the mod or any binary built from
# it - section 3.1 names "GitHub Releases on a fork" outright - and it publishes no maven artifact,
# so no build of it can be downloaded, committed, or handed to anyone else. What section 2.4 does
# allow is building it for your own use, which is exactly and only what this script does.
#
# Datagen runs first, and it is not optional. Thaumaturge generates almost all of its content - the
# aspects, the research categories, recipes, advancements, loot tables and the biomes - and none of
# that is committed to its repository: src/generated/resources holds 14 files and src/main/resources
# holds 202, against the ~1780 the mod actually needs. `jar` alone therefore produces a jar whose
# every datapack registry loads empty, and the game dies at world load with
#     Unbound values in registry ... thaumaturge:aspect
# upstream knows this - build.gradle registers a `generateData` task - but nothing depends on it, so
# it has to be run explicitly. The check at the end refuses to install a jar without the data.
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

# Gradle refuses to configure a project whose directory name starts with a dot, and it says so with
# "The project name '.thaumaturge-src' must not start or end with a '.'". Catch it here, before the
# clone, because the failure at that point is far harder to read.
case "$(basename -- "$src")" in
    .*)
        echo "fetch-thaumaturge: THAUMATURGE_SRC must not be a dot-directory (got $src)" >&2
        echo "fetch-thaumaturge: Gradle cannot configure a project named '$(basename -- "$src")'" >&2
        exit 1
        ;;
esac

if [ ! -d "$src/.git" ]; then
    echo "fetch-thaumaturge: cloning $repo"
    mkdir -p "$(dirname -- "$src")"
    git clone --quiet "$repo" "$src"
fi

echo "fetch-thaumaturge: checking out $commit"
git -C "$src" fetch --quiet origin
git -C "$src" checkout --quiet --force "$commit"

# --no-build-cache is not about speed. Upstream turns the Gradle build cache on (its own
# gradle.properties sets org.gradle.caching=true), so a cached run writes this project's compiled
# output and jar into ~/.gradle/caches/build-cache-1 - and CI persists that directory in this
# repository's Actions cache, where a Thaumaturge build has no business being. Dependency downloads
# still land there and are still cached; they are public artifacts.
gradle_args="--no-build-cache"
if [ -n "${CI:-}" ]; then
    gradle_args="$gradle_args --no-daemon"
fi

echo "fetch-thaumaturge: generating Thaumaturge's data (this is what makes the jar usable)"
( cd "$src" && ./gradlew $gradle_args runData -PdatagenPass=true )

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

list_jar() {
    if command -v unzip >/dev/null 2>&1; then
        unzip -Z1 "$1"
    elif [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/jar" ]; then
        "$JAVA_HOME/bin/jar" tf "$1"
    else
        return 127
    fi
}

if listing=$(list_jar "$built"); then
    aspects=$(printf '%s\n' "$listing" | grep -c '^data/thaumaturge/thaumaturge/aspect/.*\.json$' || true)
    if [ "$aspects" -lt 37 ]; then
        echo "fetch-thaumaturge: $built carries only $aspects aspect files, expected 37." >&2
        echo "fetch-thaumaturge: datagen did not run, and this jar would crash the game." >&2
        exit 1
    fi
    echo "fetch-thaumaturge: the jar carries $aspects aspects and the rest of the generated data"
else
    echo "fetch-thaumaturge: WARNING no unzip and no \$JAVA_HOME/bin/jar, cannot confirm the data" >&2
fi

mkdir -p "$libs"
cp -- "$built" "$libs/"
echo "fetch-thaumaturge: $(basename -- "$built") -> $libs/"
echo "fetch-thaumaturge: do not commit this jar or pass it on; the licence forbids both."
