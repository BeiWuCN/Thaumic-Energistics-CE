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

JDK 21 and `gradlew build`. Dependencies are not resolved from maven; they are read from `libs/` (see `libs/README.md` for all seven):

- Thaumaturge: <https://github.com/Leclowndu93150/Thaumaturge/> — get it from their repo or releases rather than redistributing it here
- Applied Energistics 2 19.2.17: <https://modrinth.com/mod/ae2>
- GuideME 21.1.x: <https://modrinth.com/mod/guideme>

These also live in `libs/`, for the same reason (Thaumaturge is a prerequisite mod that is not on Maven Central, so this mod does not use public maven either):

- JEI 19.57.x: <https://modrinth.com/mod/jei> — the recipe transfer button
- Jade 15.10.x: <https://modrinth.com/mod/jade> — the block info overlay
- Curios 9.5.x + TerraBlender 4.1.x: Thaumaturge's runtime prerequisites

## Credits

The original Thaumic Energistics is by Nividica and contributors; this CE continues the 1.21.1 port. Thaumaturge is Leclowndu93150's project and this addon depends on its API. High-version textures drawn by @麦淇淋.

## License

MIT, see `LICENSE`. The code derives from Thaumic Energistics, published under LGPL-3.0 — that part is still the original authors' copyright, so honour that license too when redistributing.
