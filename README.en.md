![Thaumic Energistics: CE](https://cdn.jsdelivr.net/gh/BeiWuCN/Thaumic-Energistics-CE@1.21.1/logo.png)

[中文](README.md) · [Repository](https://github.com/beiwucn/Thaumic-Energistics-CE) · [Issues](https://github.com/beiwucn/Thaumic-Energistics-CE/issues)

# Thaumic Energistics: CE

## About

A Minecraft mod about essentia and networks. It connects Thaumaturge's essentia to AE2's ME network: aspects are stored on the network, moved by it, and crafted by it.

This mod is the original Thaumic Energistics (stopped at 1.12.2) redone on 1.21.1, written against Thaumaturge's API.

Requirements: Minecraft 1.21.1, NeoForge 21.1.250 or newer, Applied Energistics 2 19.2.x, Thaumaturge 1.0.2 (the supported target for this port).

It contains essentia storage components, the essentia terminal, several essentia machines, wireless links and arcane crafting, plus a complete Thaumaturge research tree.

## Building

Building uses JDK 21 and `gradlew build`.

AE2, JEI, Jade and other Maven dependencies are pinned in `gradle.properties`.

Supply Thaumaturge 1.0.2 at `libs/thaumaturge-1.21.1-NeoForge-1.0.2.jar`, or set `-Pthaumaturge_jar=/absolute/path/to.jar`. TECE does not bundle this dependency.

Install optional compatibility mods separately as needed. Development tools and local verification scripts are not included in this repository.

## License

MIT, see `LICENSE`.

The code derives from Thaumic Energistics by Nividica, which is MIT as well. That part remains the original authors' copyright, so the upstream notice (Chris and BrockWS) is carried in `LICENSE` too; keep it there when redistributing. Thaumaturge is Leclowndu93150's project. High-version textures are drawn by @麦淇淋.

## Issues

Crashes, suggestions and bugs all go to the [issues page](https://github.com/beiwucn/Thaumic-Energistics-CE/issues).

Before submitting, make sure you are using the latest version, that the issue has not already been answered or fixed, and that it is a valid issue. Anything vanilla Minecraft, AE2 or Thaumaturge can already do is considered an invalid suggestion; asking for a smaller, more compact or more efficient version of something is considered invalid as well.

Click New Issue to start. Where the repository provides a template, fill that in: the template lists the information to add. Then click Submit New Issue and wait for feedback.

The more complete the information, the faster the issue is located and the faster a fixed version arrives. Issues that do not match these requirements may be closed.

This tracker covers the CE version on 1.21.1 / 26.1.2 only. The original Thaumic Energistics, which stopped at 1.12.2, is a different project.
