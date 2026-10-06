package thaumicenergistics_ce.menu;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.util.ConfigMenuInventory;
import com.leclowndu93150.thaumaturge.api.aspect.Aspects;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.blockentity.BlockEntityEssentiaCellWorkbench;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import thaumicenergistics_ce.item.ItemEssentiaCell;
import thaumicenergistics_ce.network.PartitionWellPayload;
import thaumicenergistics_ce.util.ThELog;

/**
 * The partition grid the cell workbench shows: what the cell holds and what a client marks in it.
 * A mark reaches the server only as PartitionWellPayload, since a well the client draws is a view
 * of the cell and never a source of writes. Every write goes through the menu's cell, and the
 * block entity is then told the cell changed.
 */
final class CellPartitionEditor {

    private final MenuEssentiaCellWorkbench menu;

    private final ConfigMenuInventory partition;

    CellPartitionEditor(MenuEssentiaCellWorkbench menu, ConfigMenuInventory partition) {
        this.menu = menu;
        this.partition = partition;
    }

    ConfigMenuInventory partition() {
        return partition;
    }

    @Nullable AEKey keyInWell(int well) {
        GenericStack stack = partition.getDelegate().getStack(well);
        return stack == null ? null : stack.what();
    }

    /** Fills every well from the aspects the cell holds; the client's half is the menu's. */
    void partitionToContents() {
        if (!menu.hasCell()) {
            ThELog.LOG.warn("[cell-partition] no cell in the workbench, so the wells cannot be filled");
            return;
        }
        ItemEssentiaCell.partitionToContents(menu.workbench.getCell());
        menu.workbench.setChanged();
        menu.broadcastChanges();
    }

    /** Empties every well; the client's half is the menu's, as in {@link #partitionToContents()}. */
    void clearPartition() {
        if (!menu.hasCell()) {
            ThELog.LOG.warn("[cell-partition] no cell in the workbench, so there is no partition to clear");
            return;
        }
        ItemEssentiaCell.clearPartition(menu.workbench.getCell());
        menu.workbench.setChanged();
        menu.broadcastChanges();
    }

    /**
     * Applies a well edit that arrived from a client, the only route that reaches the server: a
     * mark, or {@code PartitionWellPayload.CLEAR} to take one out. Refused without a cell.
     */
    void setWell(int well, ResourceLocation aspectId, Player player) {
        BlockEntityEssentiaCellWorkbench workbench = menu.workbench;
        if (workbench == null) {
            return;
        }
        if (well < 0 || well >= BlockEntityEssentiaCellWorkbench.PARTITION_SLOTS) {
            ThELog.LOG.warn("[cell-partition] well {} is out of range", well);
            return;
        }
        if (!menu.hasCell()) {
            ThELog.LOG.warn("[cell-partition] no cell in the workbench, so well {} has nowhere to go", well);
            return;
        }
        if (PartitionWellPayload.CLEAR.equals(aspectId)) {
            // A mark is taken out by clicking its well, where AE2 would pick the entry back up.
            clearWell(well);
            return;
        }

        Holder<IAspect> aspect =
                Aspects.resolve(
                        player.level(),
                        ResourceKey.create(
                                IAspect.REGISTRY_KEY, aspectId));
        if (aspect == null) {
            // An id the server does not know: dropping it beats a partition entry that can never match.
            ThELog.LOG.warn("[cell-partition] the server cannot resolve aspect {}", aspectId);
            return;
        }
        AEssentiaKey key = AEssentiaKey.of(aspect);
        if (key == null) {
            // Not registry-backed: no id, so the entry could never match anything.
            ThELog.LOG.warn("[cell-partition] aspect {} is not a registry entry", aspectId);
            return;
        }

        // One type, one well: a key already marked elsewhere moves here instead of appearing twice.
        for (int other = 0; other < BlockEntityEssentiaCellWorkbench.PARTITION_SLOTS; other++) {
            if (other != well && key.equals(keyInWell(other))) {
                clearWell(other);
            }
        }

        // One, because a partition entry is a type rather than an amount - how much the cell holds is
        // decided by its size. Writing here is what fires the block entity's listener, which stores it.
        partition.getDelegate().setStack(well, new GenericStack(key, 1));
        workbench.setChanged();
        menu.broadcastChanges();
        // Read straight back: "wrote" and "now holds" as two separate facts, for the failure being chased.
        ThELog.LOG.info(
                "[cell-partition] wrote {} to well {}; it now holds {}",
                key, well, keyInWell(well));
    }

    private void clearWell(int well) {
        partition.getDelegate().setStack(well, null);
        menu.workbench.setChanged();
        menu.broadcastChanges();
        ThELog.LOG.info("[cell-partition] took the mark out of well {}; it now holds {}", well, keyInWell(well));
    }
}
