package thaumicenergistics_ce.blockentity.inscriber;

import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import static thaumicenergistics_ce.blockentity.inscriber.BlockEntityKnowledgeInscriber.STATUS_ACTIONABLE;
import static thaumicenergistics_ce.blockentity.inscriber.BlockEntityKnowledgeInscriber.STATUS_ALREADY_STORED;
import static thaumicenergistics_ce.blockentity.inscriber.BlockEntityKnowledgeInscriber.STATUS_CORE_FULL;
import static thaumicenergistics_ce.blockentity.inscriber.BlockEntityKnowledgeInscriber.STATUS_ENCODED;
import static thaumicenergistics_ce.blockentity.inscriber.BlockEntityKnowledgeInscriber.STATUS_NO_RECIPE;
import static thaumicenergistics_ce.blockentity.inscriber.BlockEntityKnowledgeInscriber.STATUS_READY;
import static thaumicenergistics_ce.blockentity.inscriber.BlockEntityKnowledgeInscriber.STATUS_RESEARCH_LOCKED;

import com.leclowndu93150.thaumaturge.api.recipe.ResearchGate;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.arcane.ThEArcanePattern;
import thaumicenergistics_ce.inventory.HandlerKnowledgeCore;
import thaumicenergistics_ce.util.ThELog;

/**
 * 铭刻机网格解析成什么，以及它的按钮此刻会拿那份配方做什么。
 * 缓存以网格和核心为键：删配方动的是核心，不是网格。状态码都来自槽位，
 * 故标签和按钮不需要 ticker。
 */
final class InscriberResolution {

    private final BlockEntityKnowledgeInscriber inscriber;
    private final InscriberInventory inventory;

    private boolean dirty = true;
    private @Nullable ThEArcanePattern pattern;
    private int status = STATUS_READY;
    private int lastResult = STATUS_READY;

    InscriberResolution(BlockEntityKnowledgeInscriber inscriber, InscriberInventory inventory) {
        this.inscriber = inscriber;
        this.inventory = inventory;
    }

    void markDirty() {
        dirty = true;
    }

    /** 机器此刻会做什么，从槽位推导，插核心能立刻更新按钮。
     * 顺序要紧：靠前的检查是玩家得先解决的。 */
    int status() {
        if (inscriber.getLevel() == null) {
            return STATUS_READY;
        }
        refresh();
        return status;
    }

    void refresh() {
        Level level = inscriber.getLevel();
        if (level == null || level.isClientSide() || !dirty) {
            return;
        }
        dirty = false;
        recompute();
    }

    boolean canStore() {
        return status() == STATUS_ACTIONABLE;
    }

    @Nullable ThEArcanePattern pattern() {
        if (inscriber.getLevel() == null) {
            return null;
        }
        refresh();
        return pattern;
    }

    /** 把解析出的配方存进核心，清空网格。
     * @return 结果状态码，也可从 {@link #lastResult()} 拿 */
    int save(@Nullable Player player) {
        lastResult = status();
        // 缓存的新鲜度只到上一次改动通知，过期的缓存会让按钮什么都不做，屏幕上还不说原因。
        dirty = true;
        refresh();
        ThEArcanePattern resolved = pattern();
        if (resolved == null) {
            ThELog.LOG.info("[inscriber] save at {} found no recipe: status={} cells={}",
                    inscriber.getBlockPos(), status,
                    inventory.cells().stream().filter(s -> !s.isEmpty()).count());
            return lastResult = STATUS_NO_RECIPE;
        }
        HandlerKnowledgeCore core = core();
        if (core == null) {
            return lastResult;
        }
        if (player != null && !passesResearch(player, resolved)) {
            ThELog.LOG.info(
                    "[inscriber] save refused at {}: {} is gated by research this player has not unlocked",
                    inscriber.getBlockPos(), resolved.result());
            return lastResult = STATUS_RESEARCH_LOCKED;
        }
        if (!core.store(resolved)) {
            ThELog.LOG.info("[inscriber] save refused at {}: the core would not take {} (room {} of {})",
                    inscriber.getBlockPos(), resolved.result(), core.size(),
                    HandlerKnowledgeCore.MAXIMUM_STORED_PATTERNS);
            return lastResult = STATUS_CORE_FULL;
        }
        // 只说成功路径，故得放在上面的拒绝之后。
        ThELog.LOG.info("[inscriber] save at {} stored {} (status {})",
                inscriber.getBlockPos(), resolved.result(), status());
        dirty = true;
        inventory.clear();
        return lastResult = STATUS_ENCODED;
    }

    int deleteStored(@Nullable Player player) {
        HandlerKnowledgeCore core = core();
        if (core == null) {
            return lastResult = status();
        }
        // 只认网格解析出的配方：回退会删掉玩家没点过名的条目。
        ThEArcanePattern resolved = pattern();
        if (resolved == null) {
            return lastResult = status();
        }
        if (!core.removeByResult(resolved.result())) {
            return lastResult = status();
        }
        dirty = true;
        return lastResult = status();
    }

    int lastResult() {
        return lastResult;
    }

    /** 该玩家此刻能不能存下这个网格，供菜单的按钮状态用。研究属于玩家不属于方块，
     * 机器自己的状态答不了。 */
    boolean canStore(Player player) {
        dirty = true;
        refresh();
        ThEArcanePattern resolved = pattern();
        return resolved == null || passesResearch(player, resolved);
    }

    List<ItemStack> storedOutputs() {
        if (inscriber.getLevel() == null) {
            return List.of();
        }
        HandlerKnowledgeCore core = core();
        return core == null ? List.of() : core.storedOutputs();
    }

    void writeTo(ValueOutput output) {
        output.putInt("LastResult", lastResult);
    }

    void readFrom(ValueInput input) {
        lastResult = input.getIntOr("LastResult", 0);
    }

    private void recompute() {
        pattern = null;
        status = STATUS_READY;
        Level level = inscriber.getLevel();
        if (level == null) {
            return;
        }
        List<ItemStack> cells = inventory.cells();
        if (ThEArcanePattern.isGridEmpty(cells)) {
            return;
        }
        ThEArcanePattern resolved = ThEArcanePattern.resolveGrid(level, cells);
        if (resolved == null) {
            status = STATUS_NO_RECIPE;
            return;
        }
        pattern = resolved;
        HandlerKnowledgeCore core = core();
        if (core == null) {
            return;
        }
        if (core.patternFor(resolved.result()) != null) {
            status = STATUS_ALREADY_STORED;
            return;
        }
        if (!core.hasRoom()) {
            status = STATUS_CORE_FULL;
            return;
        }
        status = STATUS_ACTIONABLE;
    }

    private @Nullable HandlerKnowledgeCore core() {
        Level level = inscriber.getLevel();
        if (level == null) {
            return null;
        }
        return HandlerKnowledgeCore.of(inventory.coreStack(), level.registryAccess());
    }

    private boolean passesResearch(Player player, ThEArcanePattern resolved) {
        return resolved.gate().map(gate -> ResearchGate.passes(player, gate)).orElse(true);
    }
}
