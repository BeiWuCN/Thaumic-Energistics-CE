package thaumicenergistics_ce.network;

/**
 * Where the essentia container the player is using actually sits.
 *
 * <p>The terminal's two gestures both act on a container that stays with the player, so the server has to
 * be told which one to touch: the cursor stack and the main hand are different places, and neither is a
 * menu slot. A slot id is a menu slot index - {@code Slot.index}, not {@code getSlotIndex()}, which counts
 * within the slot's own container and names a different slot entirely once AE2's slots are in the list.
 */
public final class ContainerSlot {

    /** The stack on the cursor. */
    public static final int CURSOR = -1;

    /** The player's main hand. */
    public static final int MAIN_HAND = -2;

    private ContainerSlot() {}
}
