[中文](README.md) · [Repository](https://github.com/beiwucn/Thaumic-Energistics-CE)

# Thaumic Energistics: CE

Bridges Thaumaturge essentia and AE2's ME network, on 1.21.1 / NeoForge.

The original Thaumic Energistics stopped at 1.12.2. This is that addon redone on top of Thaumaturge — recipes, research and machine screens were mostly rewritten. CE stands for Community Edition: it started as something for our own modpacks.

You need Minecraft 1.21.1, NeoForge 21.1.250+, Applied Energistics 2 19.2.x and Thaumaturge 0.4.4+ (with its dependency GuideME). Drop the jar in `mods/`.

## What's in it

**Storage and buses**: ME essentia storage components in 1k / 4k / 16k / 64k (plus a creative one), essentia storage bus, import bus, export bus and the essentia terminal including the wireless one.

**Machines**:

- Arcane Assembly Chamber — hands arcane workbench recipes to an AE2 crafting CPU, and shows what it is crafting on its face
- Distillation Encoder — turns an item's essentia composition into a pattern
- Knowledge Inscriber + Knowledge Core — recipes live on a core, up to 21 per machine
- Infusion Provider / Infusion Monitor — the first feeds `thaumaturge:infusion`, the second sits by an altar and reports the risk
- Essentia Vibration Chamber — burns spare essentia into AE

**Wireless**: Essentia Provider with a Wireless Essentia Receiver, and a Golem Wireless Backpack that dumps what a golem carries straight into the network.

**Arcane side**: Arcane Crafting Terminal (craft arcane recipes in the terminal, the network covers the vis you lack), Focus of the AE Wrench, Vis Relay Interface.

Plus a full Thaumonomicon tree, in English and Chinese.

## Building

JDK 21 and `gradlew build`.

Eight of the dependencies are ordinary maven coordinates, pinned to exact versions in `gradle.properties` and resolved from the Modrinth maven repository. A fresh clone builds with nothing staged by hand:

- Applied Energistics 2 19.2.17: <https://modrinth.com/mod/ae2>
- GuideME 21.1.17: <https://modrinth.com/mod/guideme>
- JEI 19.57.0.444: <https://modrinth.com/mod/jei> — the recipe transfer button
- Jade 15.10.6: <https://modrinth.com/mod/jade> — the block info overlay
- Curios 9.5.1 / TerraBlender 4.1.0.8 / Lithostitched 1.8.0 / Apollib 1.2.0: Thaumaturge's runtime prerequisites; this mod imports none of them

**Thaumaturge itself is the one you have to build once.** It is All Rights Reserved: it publishes no maven artifact, and its licence forbids publishing the mod or any binary built from it (§3.1 names "GitHub Releases on a fork" outright), so this repository cannot carry it for you. §2.4 does allow building it for your own use. Run one of these:

```sh
tools/fetch-thaumaturge.sh                      # Linux, macOS, CI
powershell -File tools/fetch-thaumaturge.ps1    # Windows
```

It clones <https://github.com/Leclowndu93150/Thaumaturge/> at the commit pinned as `thaumaturge_commit` in `gradle.properties` and leaves the jar it builds in `libs/`. Without that jar `gradlew build` fails the configuration with a pointer to these commands rather than degrading into hundreds of unresolved-symbol errors. **Do not commit the jar and do not pass it on.**

## Credits

The original Thaumic Energistics is by Nividica and contributors; this CE continues the 1.21.1 port. Thaumaturge is Leclowndu93150's project and this addon depends on its API. High-version textures drawn by @麦淇淋.

## License

MIT, see `LICENSE`. The code derives from Thaumic Energistics by Nividica, which is MIT as well (the upstream `LICENSE` credits Chris and BrockWS). That part is still the original authors' copyright, so the upstream notice is carried in `LICENSE` too - keep it there when redistributing.
