---
navigation:
  title: Moving Essentia with ME Buses
  position: 20
  parent: thaumicenergistics_ce:automation.md
  icon: thaumicenergistics_ce:essentia_access_card
item_ids:
- thaumicenergistics_ce:essentia_access_card
---

# Moving Essentia with ME Buses

Choose a bus based on which side should own the stock. Fit an <ItemLink id="thaumicenergistics_ce:essentia_access_card" /> to the bus and mount it against a usable face of an essentia container.

- An <ItemLink id="ae2:import_bus" /> moves essentia from the container into ME storage.
- An <ItemLink id="ae2:export_bus" /> sends configured aspects from the network into the container.
- An <ItemLink id="ae2:storage_bus" /> makes the container accessible to the network without emptying it into a drive.

## Example: Empty a Jar into a Cell

Connect an import bus fitted with an access card to a jar. Check that the bus is online and the network has an essentia cell that accepts the jar's aspect. Open your essentia terminal and compare the jar and network readings. The jar should lose what the network gains. If you add an aspect filter, a jar containing a different aspect will stay untouched.

A storage bus behaves differently: counting the jar in the terminal does not move its contents. The jar level changes when an operation actually inserts or extracts essentia.
