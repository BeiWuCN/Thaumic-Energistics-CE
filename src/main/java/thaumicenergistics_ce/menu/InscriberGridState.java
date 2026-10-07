package thaumicenergistics_ce.menu;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.arcane.ThEArcanePattern;
import thaumicenergistics_ce.blockentity.inscriber.BlockEntityKnowledgeInscriber;
import thaumicenergistics_ce.inventory.HandlerKnowledgeCore;
import thaumicenergistics_ce.util.ThELog;

/**
 * 菜单的 3x3 配方网格：一次点击所做的写入、已存储样板的回读，以及
 * 说明网格是否仍以同样方式解析的签名。
 * 网格在两侧都存在，客户端的写入是载荷而不是槽位同步——见
 * {@code GhostGridSlot}——所以这里的写入会落到机器的容器上，或作为载荷发送。
 */
final class InscriberGridState {

    private final MenuKnowledgeInscriber menu;

    private final List<ItemStack> gridScratch = new ArrayList<>(MenuKnowledgeInscriber.CRAFT_SLOTS);

    private int sampledGrid = -1;

    private long gridSignatureTick = Long.MIN_VALUE;

    InscriberGridState(MenuKnowledgeInscriber menu) {
        this.menu = menu;
    }

    /**
     * 一个格位，来自菜单的点击路由：在服务端写机器的容器，在客户端写
     * 槽位，其载荷由服务端在下一个 tick 变成同样的写入。
     */
    void setCell(int cell, ItemStack stack) {
        if (menu.inscriber != null) {
            menu.inscriber.setGridCell(cell, stack);
            return;
        }
        menu.slots.get(MenuKnowledgeInscriber.IDX_CRAFT_START + cell).set(stack);
    }

    List<ItemStack> cells() {
        List<ItemStack> cells = new ArrayList<>(MenuKnowledgeInscriber.CRAFT_SLOTS);
        for (int i = 0; i < MenuKnowledgeInscriber.CRAFT_SLOTS; i++) {
            cells.add(menu.slotStack(MenuKnowledgeInscriber.IDX_CRAFT_START + i));
        }
        return cells;
    }

    /**
     * 每个 tick 最多重新采样一次：九个物品堆的组件太多，无法逐帧哈希，所以
     * 没有推进 tick 的那一帧会复用上一次的答案。
     */
    int signature() {
        long now = menu.playerInventory.player.level().getGameTime();
        if (now != gridSignatureTick) {
            gridSignatureTick = now;
            gridScratch.clear();
            for (int i = 0; i < MenuKnowledgeInscriber.CRAFT_SLOTS; i++) {
                gridScratch.add(menu.slotStack(MenuKnowledgeInscriber.IDX_CRAFT_START + i));
            }
            sampledGrid = StackSignatures.of(gridScratch);
        }
        return sampledGrid;
    }

    /**
     * 用配方的布局填充网格，JEI 转移与样板点击都是这么做的，且一次写入完成：
     * 每个格位一个载荷会让服务端针对一个一半还是旧配方的网格反复解析。
     */
    void fillFromRecipe(List<ItemStack> cells) {
        List<ItemStack> full = new ArrayList<>(MenuKnowledgeInscriber.CRAFT_SLOTS);
        for (int cell = 0; cell < MenuKnowledgeInscriber.CRAFT_SLOTS; cell++) {
            ItemStack stack = cell < cells.size() ? cells.get(cell) : ItemStack.EMPTY;
            full.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        }

        // 1. 本侧的网格，一次遍历完成。写入容器不会发送载荷。
        for (int cell = 0; cell < MenuKnowledgeInscriber.CRAFT_SLOTS; cell++) {
            menu.machine.setItem(BlockEntityKnowledgeInscriber.GRID_SLOT_START + cell, full.get(cell));
        }

        if (menu.inscriber != null) {
            menu.inscriber.setGrid(full);
        } else {
            MenuNetwork.sendInscriberGridFill(menu.containerId, full);
        }
    }

    /**
     * 把已存储的样板读回网格：按钮在那里起作用，所以这也是删除路径。
     * 尾部会被清空，因为无序配方的存储网格是一份紧凑的材料清单。
     */
    void loadPattern(int index) {
        List<ItemStack> cells = storedGrid(index);
        if (cells == null) {
            // 点名了所请求的凹槽，这样空凹槽就能与找错的凹槽区分开。
            ThELog.LOG.info("[inscriber] pattern well {} holds nothing to load", index);
            return;
        }
        ThELog.LOG.info(
                "[inscriber] loading pattern well {} -> {} ({} cells)",
                index,
                cells.isEmpty() ? "empty grid" : cells.getFirst(),
                cells.size());
        // 一次性替换，所以网格绝不会同时持有旧配方与新配方的混合物。
        menu.fillGridFromRecipe(cells);
    }

    /**
     * 某个凹槽中已存储样板的网格，为空时为 {@code null}。按位置从核心读取：
     * 凹槽自身的槽位从未被填充，所以它们是过时的。
     */
    private @Nullable List<ItemStack> storedGrid(int index) {
        if (index < 0) {
            return null;
        }
        HandlerKnowledgeCore core = menu.handler();
        if (core == null) {
            return null;
        }
        List<ThEArcanePattern> patterns = core.patterns();
        return index < patterns.size() ? patterns.get(index).grid() : null;
    }
}
