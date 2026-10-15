# Local dependencies

This branch targets the released Thaumaturge 1.0.2 for Minecraft 1.21.1 / NeoForge.
Place a locally obtained copy at `libs/thaumaturge-1.21.1-NeoForge-1.0.2.jar`, or pass `-Pthaumaturge_jar=/absolute/path/to.jar`.

`tools/fetch-thaumaturge.ps1 -SourceJar <path>` and `tools/fetch-thaumaturge.sh <path>` install an existing local jar. No upstream source patches are applied. The old source patches are historical reference only.

Optional development mods are installed by `tools/install-compat-mods.ps1`, with exact versions and hashes in `tools/compat-mods.lock.json`. ExtendedAE Plus is pinned to 1.6.2 because 1.6.3 needs a newer JEI API incompatible with Thaumaturge 1.0.2.

Local dependency jars are ignored by Git and are not included in the TECE jar.
