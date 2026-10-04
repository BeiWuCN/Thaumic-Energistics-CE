package thaumicenergistics_ce.menu.slot;

/**
 * Where the essentia container the player is using sits, for a terminal that must tell the server
 * which one to touch.
 * <ul>
 * <li>The cursor stack and the main hand are different places, and neither is a menu slot.</li>
 * <li>A slot id is a menu slot index - {@code Slot.index}, not {@code getSlotIndex()}, which counts
 * within the slot's own container and names a different slot once AE2's slots are in the list.</li>
 * </ul>
 */
public final class ContainerSlot {

    /** The stack on the cursor. */
    public static final int CURSOR = -1;

    /** The player's main hand. */
    public static final int MAIN_HAND = -2;

    private ContainerSlot() {}
}
