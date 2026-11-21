---
navigation:
  title: Arcane Encoding
  position: 45
  parent: thaumicenergistics_ce:index.md
  icon: thaumicenergistics_ce:arcane_encoding_terminal
item_ids:
- thaumicenergistics_ce:arcane_encoding_terminal
- thaumicenergistics_ce:wireless_arcane_encoding_terminal
- thaumicenergistics_ce:knowledge_core
---

# Arcane Encoding

<ItemLink id="thaumicenergistics_ce:arcane_encoding_terminal" /> records arcane workbench recipes into a Knowledge Core. It replaces the older Knowledge Inscriber: same output, but the ingredients are laid out the way you would at a workbench instead of being pushed into a machine's slots one at a time.

The wireless variant, <ItemLink id="thaumicenergistics_ce:wireless_arcane_encoding_terminal" />, is the same terminal in your pocket. Its grid, core and selection live on the item, so closing the screen and walking away keeps everything where it was.

## The two halves

The left half is an arcane workbench grid with the result preview beside it. The right half is the core panel: 3 columns by 7 rows of pattern slots showing what that core already holds, with the core slot directly below.

## Insert the Core

Place a <ItemLink id="thaumicenergistics_ce:knowledge_core" /> in the slot under the right-hand panel. Without a core the encode button will refuse and tell you so; the grid still works for trying recipes out.

## Encoding

1. Lay the ingredients out as you would at an arcane workbench.
2. The frame right of the arrow previews the result, and the reading above it is the Vis this pattern will record.
3. Turn on the substitution buttons if a recipe accepts a tag or a filled container and you want the pattern to accept the whole set rather than the one item it displays.
4. Press the encode button. On success the grid clears and the core gains one entry.

Encoding is gated on research: a pattern whose recipe you have not unlocked will be refused, the same way the Knowledge Inscriber refused it.

## Selecting and Deleting

Click a pattern slot on the right to select it; a coloured frame marks the selection. With the grid empty, pressing encode again deletes that entry. The small cross at the top-left only clears the grid, never the core.

Both the encode and the delete go through the same button on purpose: one click never does two things at once.

## JEI

Press `+` on an arcane recipe in JEI to fill the nine slots. Nothing is moved out of your inventory — the grid records what to encode, it does not consume it — and the resulting pattern stays in the core.

## In a network

Mounted on a cable, the terminal draws from the network it is attached to. As a wireless item, it needs a link: bind it at a Wireless Access Point, or install a Quantum Bridge Card and a paired singularity to reach a powered bridge from anywhere.

In the universal terminal from AE2WTLib, this terminal appears as one of the switchable modes.
