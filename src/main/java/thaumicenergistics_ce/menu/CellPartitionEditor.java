package thaumicenergistics_ce.menu;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.util.ConfigMenuInventory;
import com.leclowndu93150.thaumaturge.api.aspect.Aspects;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.blockentity.BlockEntityEssentiaCellWorkbench;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import thaumicenergistics_ce.item.ItemEssentiaCell;
import thaumicenergistics_ce.network.PartitionWellPayload;
import thaumicenergistics_ce.util.ThELog;

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

    void partitionToContents() {
        if (!menu.hasCell()) {
            ThELog.LOG.warn("[cell-partition] no cell in the workbench, so the wells cannot be filled");
            return;
        }
        ItemEssentiaCell.partitionToContents(menu.workbench.getCell());
        menu.workbench.setChanged();
        menu.broadcastChanges();
    }

    void clearPartition() {
        if (!menu.hasCell()) {
            ThELog.LOG.warn("[cell-partition] no cell in the workbench, so there is no partition to clear");
            return;
        }
        ItemEssentiaCell.clearPartition(menu.workbench.getCell());
        menu.workbench.setChanged();
        menu.broadcastChanges();
    }

    void setWell(int well, Identifier aspectId, Player player) {
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
            clearWell(well);
            return;
        }

        Holder<IAspect> aspect =
                Aspects.resolve(
                        player.level(),
                        ResourceKey.create(
                                IAspect.REGISTRY_KEY, aspectId));
        if (aspect == null) {
            ThELog.LOG.warn("[cell-partition] the server cannot resolve aspect {}", aspectId);
            return;
        }
        AEssentiaKey key = AEssentiaKey.of(aspect);
        if (key == null) {
            ThELog.LOG.warn("[cell-partition] aspect {} is not a registry entry", aspectId);
            return;
        }

        for (int other = 0; other < BlockEntityEssentiaCellWorkbench.PARTITION_SLOTS; other++) {
            if (other != well && key.equals(keyInWell(other))) {
                clearWell(other);
            }
        }

        partition.getDelegate().setStack(well, new GenericStack(key, 1));
        workbench.setChanged();
        menu.broadcastChanges();
    }

    private void clearWell(int well) {
        partition.getDelegate().setStack(well, null);
        menu.workbench.setChanged();
        menu.broadcastChanges();
    }
}
