---
navigation:
  title: Distillation Patterns
  position: 30
  parent: thaumicenergistics_ce:automation.md
  icon: thaumicenergistics_ce:distillation_encoder
item_ids:
- thaumicenergistics_ce:distillation_encoder
---

# Distillation Patterns

Use the <ItemLink id="thaumicenergistics_ce:distillation_encoder" /> when a processing pattern needs to describe essentia as an output.

1. Put a blank pattern in the encoder.
2. Set the source item. You can drag it from JEI into the source slot without moving an actual item there.
3. Review the listed aspects and amounts. Encode the pattern for the yields that your processing setup will return.
4. Insert the pattern in a supported provider and route the actual distillation outputs back into ME storage.

The encoded yield tells AE2 what to wait for. It does not run the furnace or return essentia by itself. If the machine finishes but the job stays pending, compare the aspects and amounts in the pattern with what actually reached the network.
