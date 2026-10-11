# Thaumic Energistics: CE

Thaumic Energistics: CE connects Thaumaturge's essentia and Vis to Applied Energistics 2's ME network. Store aspects in one place, find and withdraw them through terminals, and let the network supply materials for alchemy, infusion and arcane crafting.

The project continues the original Thaumic Energistics gameplay using Thaumaturge on newer Minecraft versions. This branch targets **Minecraft 26.1.2 / NeoForge**. For Minecraft 1.21.1, see the [1.21.1 branch](https://github.com/BeiWuCN/Thaumic-Energistics-CE/tree/1.21.1).

[中文](README.md) · [Downloads](https://github.com/BeiWuCN/Thaumic-Energistics-CE/releases) · [Changelog](CHANGELOG-2.7.4.4.md) · [Issues](https://github.com/BeiWuCN/Thaumic-Energistics-CE/issues)

<img src="https://raw.githubusercontent.com/BeiWuCN/Thaumic-Energistics-CE/26.1.2/src/main/resources/icon.png" alt="Thaumic Energistics: CE" width="420">

## Features

- **Essentia storage and transfer:** store aspects in ME Essentia Cells and fit Essentia Access Cards to ME Import, Export and Storage Buses to connect external essentia containers.
- **Essentia terminals:** browse network aspects, search names in your language, and deposit or withdraw essentia using containers. The wireless terminal provides remote access.
- **Arcane crafting:** wired and wireless arcane terminals provide wand and crystal slots with crafting cost displays. The Arcane Assembler handles automated crafting.
- **Vis relay connections:** bind a Vis Connection Card to a Vis Relay Interface output to recharge the wand in the terminal. Placed arcane terminals draw VIS directly from their own chunk without a card. Wireless arcane terminals share only the bound terminal's Vis Connection Card effect through a read-only indicator; the physical card stays in the placed terminal, and other upgrades remain independent. The output must be loaded and online.
- **Alchemy and infusion supply:** use the Alchemy Provider, Infusion Provider and related devices to supply Thaumaturge crafting from the ME network.
- **Golem wireless backpacks:** let golems exchange items with the network and choose backpack appearances using supported materials.
- **Research and configuration:** unlock content through the Thaumonomicon and adjust machine values and feature switches on the server.

## Installation

Download the mod JAR containing `26.1.2-neoforge` in its name from [Releases](https://github.com/BeiWuCN/Thaumic-Energistics-CE/releases) and place it in your instance's `mods` folder. The `-sources.jar` contains source code and is not an installation file.

The current release was built and tested with:

| Component | Version |
| --- | --- |
| Minecraft | 26.1.2 |
| NeoForge | 26.1.2.112 |
| Java | 25 |
| Applied Energistics 2 | 26.1.13-beta |
| Thaumaturge | 1.0.3, source revision `80903dd9` |

AE2 and Thaumaturge are required. JEI provides recipe viewing and transfer, Jade provides machine tooltips, and Curios adds accessory slots for wireless terminals. Configure the wireless terminal shortcuts in AE2's controls category; both are unbound by default.

Server settings are stored in `config/thaumicenergistics_ce-server.toml`. `terminals.restrictEssentiaVisibility` is disabled by default; enabling it hides essentia in ordinary ME terminals. Essentia access in arcane terminals is disabled by default. Enable `terminals.arcaneEssentiaAccess` and install an Essentia Access Card to use it. Wireless terminals spending AE to draw environmental VIS is disabled by default. Placed terminals draw their own chunk VIS directly, independently of this setting. The two cards serve different purposes.

## Building from source

Use JDK 25. Build the pinned Thaumaturge dependency first, then build this mod.

Windows:

```powershell
powershell -File tools/fetch-thaumaturge.ps1
.\gradlew.bat build
```

Linux / macOS:

```sh
./tools/fetch-thaumaturge.sh
./gradlew build
```

Dependency versions and the Thaumaturge revision are pinned in `gradle.properties`. The script fetches Thaumaturge's source, applies the project patches, generates its data and places the dependency JAR in `libs`. This mod's artifacts are written to `build/libs` and do not bundle Thaumaturge binaries.

## Reporting issues

Report bugs or suggestions through [Issues](https://github.com/BeiWuCN/Thaumic-Energistics-CE/issues). Include your Minecraft, NeoForge, TECE, AE2 and Thaumaturge versions, reproduction steps, and logs or a crash report. Screenshots help with interface problems.

This repository handles the CE versions. Report issues with the original Thaumic Energistics to its own project.

## License and credits

This project is licensed under [MIT](LICENSE). Parts of the code derive from Nividica's [Thaumic Energistics](https://github.com/Nividica/ThaumicEnergistics); the original copyright notices for Chris and BrockWS are preserved in the license file.

Thaumaturge is developed by Leclowndu93150. Textures for the newer versions are drawn by @麦淇淋. Thaumaturge has its own license; follow the upstream terms. This repository does not distribute its binaries.
