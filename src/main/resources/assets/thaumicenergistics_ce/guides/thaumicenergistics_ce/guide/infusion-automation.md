---
navigation:
  title: Infusion Automation
  position: 10
  parent: thaumicenergistics_ce:automation.md
  icon: thaumicenergistics_ce:infusion_provider
item_ids:
- thaumicenergistics_ce:infusion_provider
- thaumicenergistics_ce:occult_monitor
---

# Infusion Automation

An <ItemLink id="thaumicenergistics_ce:infusion_provider" /> lets a Thaumaturge altar draw essentia directly from an ME network. An <ItemLink id="thaumicenergistics_ce:occult_monitor" /> checks the altar before you start and emits a redstone pulse after a successful infusion.

<GameScene zoom="3" interactive={true} background="transparent">
  <ImportStructure src="assets/infusion-setup.snbt" />
  <IsometricCamera yaw="205" pitch="30" />
  <LineAnnotation color="#9b78ff" from="1.5 2 5.5" to="3.5 3.5 2.5" alwaysOnTop={true} thickness="0.06" />
  <LineAnnotation color="#f0bd62" from="5.5 2 5.5" to="3.5 3.5 2.5" alwaysOnTop={true} thickness="0.06" />
</GameScene>

Purple shows the infusion provider supplying the matrix. Gold shows the occult monitor watching the altar. Drag the scene to see the ME cable behind both devices.

## Connect the Altar

1. Build the infusion altar shown in the Thaumonomicon. The matrix is two blocks above the central pedestal. Form the four diagonal pillars with Salis Mundus, then leave room for a symmetrical ring of ingredient pedestals.
2. Connect the infusion provider to a powered ME network within 12 blocks of the matrix. Check that the network has a free channel and the required essentia.
3. Add an occult monitor within 12 blocks of the same matrix. Connect the monitor to ME and insert a Thaumonomicon.
4. Place the catalyst on the central pedestal and the other ingredients on the surrounding pedestals. Check the monitor for missing items, missing essentia, and instability before starting the infusion with your wand.

The provider draws from available network stock as the altar requests it. It has no internal supply to refill and does not accept essentia from tubes. You can keep jars near the altar while setting up the network.

## Worked Example: Primal Staff Core

This Thaumaturge recipe uses eight outer ingredients and seven aspects. It is a useful test of the supply line because the same altar has to draw from several different stocks.

### Set Up the Pedestals

- **Center:** one Silverwood Wand Core.
- **Outside:** two Primal Charms, plus one each of Obsidian, Ice, Quartz, Reed, Blaze and Bone Wand Cores.
- Use eight outer pedestals, arranged in opposite pairs. Put the two charms across from each other and fill the other three pairs with the six cores. Keep unused pedestals empty.

### Stock the Network and Start

1. Open the essentia terminal and check for **32 each of Aer, Ignis, Aqua, Terra, Ordo and Perditio**, plus **64 Praecantatio**. These are the base recipe amounts (256 total). Altar modifiers or a modpack can change the cost, so check the monitor before starting.
2. Insert the materials and check that the monitor sees this specific recipe and reports no shortages. This recipe has a base instability of **8**. Keep the altar layout symmetric and check the monitor's stability reading before committing expensive components.
3. Start the matrix with your casting tool. Watch the remaining essentia count while the altar draws from ME. If it stops making progress, check which aspect is missing and refill it before allowing another batch.
4. When the monitor pulses, confirm that the Primal Staff Core is on the central pedestal. Remove the result and check that the pedestal is empty before loading the next catalyst.

## Use the Completion Signal

A successful infusion sends a short pulse from the monitor watching that matrix. Use it to signal a buffer or permit your item-handling setup to move on to the next batch. The provider handles essentia and the monitor reports completion. Loading pedestals, starting the ritual, and collecting the result remain separate steps. If you automate them with other devices, test those steps separately and only queue one batch at a time until you have verified the setup.

## If the Infusion Stalls

- **The monitor cannot find the altar:** check its book, channel, and distance to the matrix. If you have several altars nearby, check which one it is watching.
- **Essentia is missing:** check the provider's channel and search for the required aspect in the essentia terminal. Filled phials in item storage do not count as loose essentia.
- **The recipe is not recognized:** check the central item and all surrounding materials against JEI. Keep the pedestal layout balanced to manage instability.
- **There is no completion pulse:** the monitor signals a successful craft on its linked altar. If the ritual failed or never started, troubleshoot the altar before advancing the next batch.
