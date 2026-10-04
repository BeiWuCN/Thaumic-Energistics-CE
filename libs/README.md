# libs/

Everything here is excluded from the repository by `.gitignore`. Exactly one dependency is resolved
from this directory; the rest are maven coordinates pinned in `gradle.properties`.

**Thaumaturge is the exception, and not by choice.** Its LICENSE is All Rights Reserved: §3.1 forbids
publishing the mod *or any binary built from it* — naming "GitHub Releases on a fork", file hosts and
modpacks explicitly — and it publishes no maven artifact, so no build of it can be downloaded,
committed, or handed to anyone else. §2.4 does allow building it for your own use, which is what lands
the jar here.

`build.gradle` fails the configuration with a pointed message when no `thaumaturge-*.jar` is present.
Run one of these once to put it there:

```sh
tools/fetch-thaumaturge.sh                      # Linux, macOS, CI
powershell -File tools/fetch-thaumaturge.ps1    # Windows
```

Both clone <https://github.com/Leclowndu93150/Thaumaturge/> at the `thaumaturge_commit` pinned in
`gradle.properties` — the commit this addon's 223 Thaumaturge imports were written against — build it
with its own wrapper, and copy the resulting jar back here. They are idempotent; pass `--force` / `-Force`
to rebuild.

**Do not commit the jar and do not pass it on.** `.gitignore` already refuses `libs/*.jar`.

## It runs the data generator too, and it has to

Building Thaumaturge with nothing but `gradlew jar` produces a jar that loads and then kills the game.
Upstream registers a `generateData` task but nothing depends on it, so `src/generated/resources` stays
empty unless somebody runs it by hand, and the jar ends up carrying 216 data files instead of roughly
1780. Every datapack registry Thaumaturge declares is then empty and the first world load dies inside
`RegistryDataLoader` with

    Unbound values in registry ResourceKey[minecraft:root / thaumaturge:aspect]: [thaumaturge:aer, ...]

Both scripts run `runData -PdatagenPass=true` before `jar` for that reason, and then refuse to install a
jar carrying fewer than 37 aspect files. If you build Thaumaturge yourself, do the same — otherwise you
will spend an evening chasing a crash that looks like a bug in this addon.

## Why a commit and not a version

The upstream repository has no tags and no releases, so there is no version to pin and no artifact to
name. `thaumaturge_commit` is the newest source at the time of writing, on branch `1.21.1`. Earlier
builds (0.4.6 and 0.4.4) were used during development and are kept outside the repository; 0.4.6 is
the revision that first required Lithostitched.

## What is no longer here

AE2, GuideME, JEI, Jade, Curios, TerraBlender, Lithostitched and Apollib used to be flat files in this
directory too. They are now maven coordinates against `https://api.modrinth.com/maven`, so a fresh clone
resolves them itself. Lithostitched and Apollib are worth knowing about even so: Thaumaturge 0.4.6
declared Lithostitched `[1.8.0,)` as a hard dependency and Lithostitched in turn requires Apollib
`[1.2.0,)`. Lithostitched's Modrinth pom lists no dependencies at all, so Gradle never pulls Apollib in
transitively — it is declared explicitly for that reason. When a `ModList` looks wrong by hand, note that
`ModSorter` names only one missing mod per pass, so Lithostitched has to be satisfied before Apollib is
even mentioned.
