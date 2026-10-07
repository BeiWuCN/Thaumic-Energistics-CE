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
 * 存储元件工作台显示的分区网格：元件持有什么，以及客户端在其中标记什么。
 * 标记只以 PartitionWellPayload 的形式到达服务端，因为客户端绘制的凹槽是元件
 * 的一个视图，永远不是写入的来源。所有写入都经过菜单的元件，随后
 * 再告知方块实体元件已改变。
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

    /** 用元件持有的要素填满每一个凹槽；客户端的这一半由菜单负责。 */
    void partitionToContents() {
        if (!menu.hasCell()) {
            ThELog.LOG.warn("[cell-partition] no cell in the workbench, so the wells cannot be filled");
            return;
        }
        ItemEssentiaCell.partitionToContents(menu.workbench.getCell());
        menu.workbench.setChanged();
        menu.broadcastChanges();
    }

    /** 清空每一个凹槽；客户端的这一半由菜单负责，与 {@link #partitionToContents()} 相同。 */
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
     * 应用一个从客户端到达的凹槽编辑，这是唯一到达服务端的路径：一个
     * 标记，或用 {@code PartitionWellPayload.CLEAR} 取下一个。没有元件时拒绝。
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
            // 点击标记所在的凹槽即可取下它，AE2 在这里的做法是把该条目捡回来。
            clearWell(well);
            return;
        }

        Holder<IAspect> aspect =
                Aspects.resolve(
                        player.level(),
                        ResourceKey.create(
                                IAspect.REGISTRY_KEY, aspectId));
        if (aspect == null) {
            // 服务端不认识的 id：丢弃它总好过留下一个永远无法匹配的分区条目。
            ThELog.LOG.warn("[cell-partition] the server cannot resolve aspect {}", aspectId);
            return;
        }
        AEssentiaKey key = AEssentiaKey.of(aspect);
        if (key == null) {
            // 不是注册表支持的：没有 id，所以该条目永远无法匹配任何东西。
            ThELog.LOG.warn("[cell-partition] aspect {} is not a registry entry", aspectId);
            return;
        }

        // 一个类型一个凹槽：已在别处标记的 [key] 会移到这里，而不是出现两次。
        for (int other = 0; other < BlockEntityEssentiaCellWorkbench.PARTITION_SLOTS; other++) {
            if (other != well && key.equals(keyInWell(other))) {
                clearWell(other);
            }
        }

        // 写 1，因为分区条目是一个类型而不是数量——元件持有多少是由
        // 它的容量决定的。在这里写入才会触发方块实体的监听器，由它来存储。
        partition.getDelegate().setStack(well, new GenericStack(key, 1));
        workbench.setChanged();
        menu.broadcastChanges();
        // 立刻回读：把 "wrote" 与 "now holds" 作为两个独立的事实，为的是正在追查的故障。
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
