package thaumicenergistics_ce.menu.slot;

/**
 * Where the essentia container the player is using sits, for a terminal that must tell the server which.
 * <ul>
 *   <li>The cursor stack and the main hand are different places, and neither is a menu slot.
 *   <li>A slot id is {@code Slot.index}, not {@code getSlotIndex()}, which counts within the slot's own
 *       container and names a different slot once AE2's slots join the list.
 * </ul>
 */
public final class ContainerSlot {

    /** The stack on the cursor. */
    public static final int CURSOR = -1;

    /** The player's main hand. */
    public static final int MAIN_HAND = -2;

    private ContainerSlot() {}
}
