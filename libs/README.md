# libs/

Flat-file mod dependencies. Neither Thaumaturge nor AE2 is reliably resolvable from a public
maven repository, and a flat file guarantees the addon compiles against exactly the jar it was
written for. Everything the build needs lives here - `build.gradle` declares no mod maven
coordinate at all.

| File | Why |
| --- | --- |
| `thaumaturge-1.21.1-NeoForge-BETA-0.4.7.jar` | Supplies the arcane recipe API, aspects, aura and essentia. 199 imports. Built from git branch `1.21.1` commit `9acb9143a9fb86f1a6cba55f1284ecbd45825fba` ("add essentia priority api"), the newest source at the time of writing; the previous 0.4.6 and 0.4.4 jars are kept in `../build/backup/`. |
| `appliedenergistics2-19.2.17.jar` | Supplies the ME network, crafting job and pattern APIs. 343 imports. |
| `jei-1.21.1-neoforge-19.57.0.444.jar` | The `integration.jei` recipe transfers. 33 imports. |
| `Jade-1.21.1-NeoForge-15.10.6.jar` | The `integration.jade` block-entity tooltip providers. 28 imports. |
| `guideme-21.1.17.jar` | Hard dependency of AE2 19.2.x; needed on the compile classpath because AE2's API references its types. |
| `curios-neoforge-9.5.1+1.21.1.jar` | Not imported by this mod. Staged for the dev run because Thaumaturge requires it at runtime. |
| `TerraBlender-neoforge-1.21.1-4.1.0.8.jar` | Not imported by this mod. Staged for the dev run because Thaumaturge's world generation requires it. |

`build.gradle` picks all of these up with:

```groovy
implementation fileTree(dir: 'libs', include: ['*.jar'])
```

## Where to get them

- **Thaumaturge** - <https://github.com/Leclowndu93150/Thaumaturge/>. Build it with `gradlew jar` or
  take a release from there and put the jar here; it is not redistributed with this mod.
- **AE2 19.2.17** - <https://cdn.modrinth.com/data/XxWD5pD3/versions/kfyIqgJ6/appliedenergistics2-19.2.17.jar>
- **GuideME 21.1.17** - <https://modrinth.com/mod/guideme/versions> (any 21.1.x; the file vendored
  here is `guideme-21.1.17.jar`)
- **JEI / Jade / Curios / TerraBlender** - their own Modrinth or CurseForge pages. Curios and
  TerraBlender are here only so the dev run starts; Thaumaturge requires both.

## Why these are not maven dependencies

Curios, TerraBlender and JEI *are* publicly resolvable, and older revisions of this file claimed
they were therefore declared as normal maven dependencies. They are not, and that claim was wrong:
`build.gradle` resolves every mod from this directory, including the runtime-only ones. Keeping them
flat is deliberate - it is the only way the dev run and a built jar see the same versions.
