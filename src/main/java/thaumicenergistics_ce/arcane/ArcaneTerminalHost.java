package thaumicenergistics_ce.arcane;

import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;

/**
 * A menu host that can name the arcane crafting terminal behind it: the placed part itself, or a
 * wireless item that remembers one it was paired with. The menu asks this rather than the host's
 * class, so the wired and the wireless terminal build the same grid, wand slot and crystals - the
 * state a player sees is the placed terminal's own.
 */
public interface ArcaneTerminalHost {

    @Nullable PartArcaneCraftingTerminal arcaneTerminal();
}
