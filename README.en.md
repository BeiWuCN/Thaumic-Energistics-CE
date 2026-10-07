![Thaumic Energistics: CE](https://cdn.jsdelivr.net/gh/BeiWuCN/Thaumic-Energistics-CE@2.0-Renewed/src/main/resources/icon.png)

[中文](README.md) · [Repository](https://github.com/beiwucn/Thaumic-Energistics-CE) · [Issues](https://github.com/beiwucn/Thaumic-Energistics-CE/issues)

# Thaumic Energistics: CE

## About

A Minecraft mod about essentia and networks. It connects Thaumaturge's essentia to AE2's ME network: aspects are stored on the network, moved by it, and crafted by it.

This mod is the original Thaumic Energistics (stopped at 1.12.2) redone on 1.21.1, written against Thaumaturge's API.

Requirements: Minecraft 1.21.1, NeoForge 21.1.250 or newer, Applied Energistics 2 19.2.x, Thaumaturge 0.4.4 or newer.

It contains essentia storage components, the essentia terminal, several essentia machines, wireless links and arcane crafting, plus a complete Thaumaturge research tree.

## Building

Building uses JDK 21 and `gradlew build`.

The dependencies come from the Modrinth maven repository with their versions pinned in `gradle.properties`: Applied Energistics 2, JEI, Jade, Thaumaturge.

Thaumaturge itself has to be built locally once. It ships as All Rights Reserved, publishes no maven artifact, and its licence forbids distributing it or any binary built from it, so this repository does not carry it. Section 2.4 of that licence permits building it for your own use:

```sh
tools/fetch-thaumaturge.sh                      # Linux, macOS, CI
powershell -File tools/fetch-thaumaturge.ps1    # Windows
```

The script clones <https://github.com/Leclowndu93150/Thaumaturge/> at the commit named by `thaumaturge_commit` in `gradle.properties` and puts the jar it builds into `libs/`. With that jar present the commands are not needed; without it `gradlew build` stops during configuration and prints them. The jar is never committed and never passed on.

## License

MIT, see `LICENSE`.

The code derives from Thaumic Energistics by Nividica, which is MIT as well. That part remains the original authors' copyright, so the upstream notice (Chris and BrockWS) is carried in `LICENSE` too; keep it there when redistributing. Thaumaturge is Leclowndu93150's project. High-version textures are drawn by @麦淇淋.

## Issues

Crashes, suggestions and bugs all go to the [issues page](https://github.com/beiwucn/Thaumic-Energistics-CE/issues).

Before submitting, make sure you are using the latest version, that the issue has not already been answered or fixed, and that it is a valid issue. Anything vanilla Minecraft, AE2 or Thaumaturge can already do is considered an invalid suggestion; asking for a smaller, more compact or more efficient version of something is considered invalid as well.

Click New Issue to start. Where the repository provides a template, fill that in: the template lists the information to add. Then click Submit New Issue and wait for feedback.

The more complete the information, the faster the issue is located and the faster a fixed version arrives. Issues that do not match these requirements may be closed.

This tracker covers the CE version on 1.21.1 / 26.1.2 only. The original Thaumic Energistics, which stopped at 1.12.2, is a different project.
