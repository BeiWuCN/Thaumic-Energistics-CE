package thaumicenergistics_ce.menu;

import java.util.function.IntSupplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.blockentity.inscriber.BlockEntityKnowledgeInscriber;

final class KnowledgeInscriberReadings implements ContainerData {

    private static final int COUNT = 2;

    private final @Nullable BlockEntityKnowledgeInscriber inscriber;

    private final Player player;

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
