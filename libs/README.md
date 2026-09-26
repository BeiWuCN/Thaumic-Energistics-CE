# libs/

Flat-file mod dependencies. Neither Thaumaturge nor AE2 is reliably resolvable from a public
maven repository, and a flat file guarantees the addon compiles against exactly the jar it was
written for.

| File | Why |
| --- | --- |
| `thaumaturge-*.jar` | Supplies the arcane recipe API, aspects, aura and essentia. Not required to build the jar, but required at runtime. |
| `appliedenergistics2-19.2.17.jar` | Supplies the ME network, crafting job and pattern APIs. |
| `guideme-21.1.17.jar` | Hard dependency of AE2 19.2.x; needed on the compile classpath because AE2's API references its types. |

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

## Runtime-only mods

`build.gradle` additionally pulls Curios, TerraBlender and JEI for the dev runs. Those *are*
publicly resolvable and are declared as normal maven dependencies, so they do not belong here.
