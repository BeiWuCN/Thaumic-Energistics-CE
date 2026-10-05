package thaumicenergistics_ce.menu;

import java.util.function.IntSupplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.blockentity.BlockEntityKnowledgeInscriber;

/**
 * The knowledge inscriber's two readings, as the menu hands them to the screen: on the server they
 * come off the machine and its core slot, on the client they are what the server last sent, and
 * {@code set} keeps what it is given because a slot sync calls it on the client.
 */
final class KnowledgeInscriberReadings implements ContainerData {

    private static final int COUNT = 2;

    private final @Nullable BlockEntityKnowledgeInscriber inscriber;

    /** Who the status is read for: whether the recipe may be stored is checked against this player. */
    private final Player player;

    /** Whether the core slot holds a core. Only the menu can see its own slots. */
    private final IntSupplier coreInSlot;

    private final int[] mirrored = new int[COUNT];

    KnowledgeInscriberReadings(
            @Nullable BlockEntityKnowledgeInscriber inscriber, Player player, IntSupplier coreInSlot) {
        this.inscriber = inscriber;
        this.player = player;
        this.coreInSlot = coreInSlot;
    }

    @Override
    public int get(int index) {
        if (inscriber == null) {
            return index >= 0 && index < mirrored.length ? mirrored[index] : 0;
        }
        return switch (index) {
            case MenuKnowledgeInscriber.DATA_HAS_CORE -> coreInSlot.getAsInt();
            case MenuKnowledgeInscriber.DATA_STATE -> status();
            default -> 0;
        };
    }

    /**
     * The machine's status, except that ACTIONABLE becomes RESEARCH_LOCKED for a player who may not
     * store the recipe: the button's tooltip reads this, and ACTIONABLE means storable, not permitted.
     */
    private int status() {
        int status = inscriber.status();
        if (status == BlockEntityKnowledgeInscriber.STATUS_ACTIONABLE && !inscriber.canStore(player)) {
            return BlockEntityKnowledgeInscriber.STATUS_RESEARCH_LOCKED;
        }
        return status;
    }

    @Override
    public void set(int index, int value) {
        if (index >= 0 && index < mirrored.length) {
            mirrored[index] = value;
        }
    }

    @Override
    public int getCount() {
        return COUNT;
    }
}
