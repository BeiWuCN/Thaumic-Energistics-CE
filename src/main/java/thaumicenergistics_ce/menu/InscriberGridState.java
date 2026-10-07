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
 * 菜单的 3x3 配方网格：点击写入、回读已存样板、判断网格解析方式是否未变的签名。
 * 两半都持有网格；客户端写入走载荷（见 {@code GhostGridSlot}），落点是机器容器或一个载荷。
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
     * 写一个格位，按菜单的点击路由分发：服务端落机器容器，客户端落槽位。
     * 客户端的写入由服务端在下一个 tick 用载荷补齐。
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
     * 每个 tick 至多重算一次：九个物品堆的组件逐帧哈希太贵。
     * 未推进 tick 的帧复用上一次的结果。
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
     * 用配方的布局填网格，JEI 转移和样板点击都走这里，一次写完。
     * 每格一个载荷会让服务端拿半旧的网格反复解析配方。
     */
    void fillFromRecipe(List<ItemStack> cells) {
        List<ItemStack> full = new ArrayList<>(MenuKnowledgeInscriber.CRAFT_SLOTS);
        for (int cell = 0; cell < MenuKnowledgeInscriber.CRAFT_SLOTS; cell++) {
            ItemStack stack = cell < cells.size() ? cells.get(cell) : ItemStack.EMPTY;
            full.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        }

        // 1. 本侧网格，一次遍历写完；写容器不会发载荷。
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
     * 把已存样板读回网格：按钮落在那里，这同时是删除路径。
     * 尾部要清空，无序配方的存储网格是一份紧凑的材料清单。
     */
    void loadPattern(int index) {
        List<ItemStack> cells = storedGrid(index);
        if (cells == null) {
            // 日志里点名所请求的凹槽，好把空凹槽与找错的凹槽分开。
            ThELog.LOG.info("[inscriber] pattern well {} holds nothing to load", index);
            return;
        }
        ThELog.LOG.info(
                "[inscriber] loading pattern well {} -> {} ({} cells)",
                index,
                cells.isEmpty() ? "empty grid" : cells.getFirst(),
                cells.size());
        // 一次性替换，网格里不会混着旧配方和新配方。
        menu.fillGridFromRecipe(cells);
    }

    /**
     * 某个凹槽里已存样板的网格，没有时为 {@code null}。
     * 按位置从核心读：凹槽自己的槽位从没被填过，是过时的。
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
