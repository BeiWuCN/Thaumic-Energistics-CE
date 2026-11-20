---
navigation:
  title: Essentia and Arcane Automation
  position: 50
  parent: thaumicenergistics_ce:index.md
  icon: thaumicenergistics_ce:alchemy_provider
item_ids:
- thaumicenergistics_ce:alchemy_provider
- thaumicenergistics_ce:arcane_assembler
- thaumicenergistics_ce:knowledge_core
- thaumicenergistics_ce:knowledge_inscriber
---

# Essentia and Arcane Automation

Start with the part of the process you want the network to take over.

- [Moving Essentia with ME Buses](essentia-buses.md) explains how to import, export, or expose a container's stock.
- [Infusion Automation](infusion-automation.md) builds an ME-fed altar and adds a completion signal.
- [Distillation Patterns](distillation-patterns.md) records the outputs of a distillation setup for ME processing.

## Alchemy

An <ItemLink id="thaumicenergistics_ce:alchemy_provider" /> supplies the essentia requested by adjacent compatible alchemical equipment. Unlike an export bus, it follows the equipment's requests as recipes change. If a request stalls, check the matching aspect in the network first.

## Arcane Assembly

Use an <ItemLink id="thaumicenergistics_ce:knowledge_inscriber" /> to record a researched recipe. Put the Knowledge Core in the inscriber, lay out the ingredients as you would at the arcane workbench, and save once the result matches. Bring the core to an <ItemLink id="thaumicenergistics_ce:arcane_assembler" /> and arrange the item and Vis supply before submitting a job.
