package thaumicenergistics_ce.blockentity.assembler;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.arcane.ArcanePatternDetails;
import thaumicenergistics_ce.arcane.ThEArcanePattern;
import thaumicenergistics_ce.compat.thaumaturge.TcRegistry;
import thaumicenergistics_ce.util.ThELog;

/**
 * 奥术组装机的一次合成，可以接收、定价、取回：花多少 vis、欠哪些晶体、
 * 灵气付不付得起、被存档打断的那次怎么收尾。
 * {@link AssemblerCraftRunner} 跑本类接收的合成。
 */
final class AssemblerCraftJob {

    private static final float MIN_CONSUMPTION_MODIFIER = 0.1F;

    private final BlockEntityArcaneAssembler owner;

    AssemblerCraftJob(BlockEntityArcaneAssembler owner) {
        this.owner = owner;
    }

    /** 样板合成要交付的晶体，按将向网络请求的物品算。 */
    static List<ItemStack> crystalStacksOf(ThEArcanePattern pattern) {
        List<ItemStack> stacks = new ArrayList<>();
        for (AspectInstance crystal : pattern.crystalItems().entries()) {
            ItemStack stack = TcRegistry.crystalFor(crystal.aspect(), crystal.amount());
            if (!stack.isEmpty()) {
                stacks.add(stack);
            }
        }
        return List.copyOf(stacks);
    }

    /** 为 {@code pattern} 收的 vis，已算装备折扣，下限是 Thaumaturge 自己的 {@code MIN_CONSUMPTION_MODIFIER}，
     * 装了装备的组装机也要付。 */
    public int craftCost(ThEArcanePattern pattern) {
        float modifier = Math.max(1.0F - owner.upgrades.gearDiscount() / 100.0F, MIN_CONSUMPTION_MODIFIER);
        return Math.max(1, (int) Math.ceil(pattern.chargedVis() * modifier));
    }

    /** 灵气付不起 {@code pattern} 就拒绝，不保留：合成 CPU 会跳过忙碌的供应器，
     * 保留等于卡死整个计划。灵气低则等待，基础值会回升。 */
    boolean canEverPay(ThEArcanePattern pattern) {
        int capacity = owner.vis.auraCapacity();
        // 0 表示区块还没初始化；中继点也算，它的 vis 在节点里。
        return capacity <= 0
                || owner.vis.relayNetworkInReach()
                || owner.vis.interfaceInReach()
                || craftCost(pattern) <= capacity;
    }

    void noteRefusal(Component why) {
        if (why.equals(owner.craft.lastRefusal())) {
            return;
        }
        owner.craft.setLastRefusal(why);
        // getString() 按服务端语言解析；每个键都有英文回退。
        ThELog.LOG.info("[assembler] at {} turned a job away: {}", owner.getBlockPos(), why.getString());
    }

    Component cannotPay(int price) {
        return AssemblerStatus.tooExpensive(price, owner.vis.auraCapacity());
    }

    @Nullable ThEArcanePattern resolveExternal(IPatternDetails details) {
        if (owner.getLevel() == null) {
            return null;
        }
        List<GenericStack> outputs = details.getOutputs();
        if (outputs.size() != 1 || !(outputs.getFirst().what() instanceof AEItemKey outputKey)) {
            return null;
        }
        List<ItemStack> inputs = new ArrayList<>();
        for (IPatternDetails.IInput input : details.getInputs()) {
            GenericStack[] possible = input.getPossibleInputs();
            if (possible.length == 0 || !(possible[0].what() instanceof AEItemKey itemKey)) {
                return null;
            }
            inputs.add(itemKey.toStack((int) Math.min(Integer.MAX_VALUE, possible[0].amount())));
        }
        return ThEArcanePattern.fromEncoded(owner.getLevel(), inputs, outputKey.getReadOnlyStack());
    }

    @Nullable ThEArcanePattern patternForResult(ItemStack result) {
        if (result.isEmpty()) {
            return null;
        }
        if (owner.patternCache.isStale()) {
            // 重建时不结算过期标志：在读到 level 之前读到的集合会定下来。
            owner.patternCache.rebuild();
        }
        for (IPatternDetails details : owner.patternCache.patterns()) {
            if (details instanceof ArcanePatternDetails arcane
                    && ItemStack.isSameItemSameComponents(arcane.pattern().result(), result)) {
                return arcane.pattern();
            }
        }
        return null;
    }

    boolean beginCraft(ThEArcanePattern pattern) {
        // 现在就固定，不在完成时重算：合成存档后不靠核心也能继续，价格和晶体随样板一并交付。
        owner.craft.begin(pattern, craftCost(pattern), crystalStacksOf(pattern));
        owner.displaySync.refreshDisplaySlots(pattern.result().copy(), pattern.grid());
        owner.setChanged();
        owner.displaySync.markForUpdate();
        // 唤醒网格：实测休眠时推入的合成每秒只 tick 一次，不是 20 次。
        ICraftingProvider.requestUpdate(owner.mainNode);
        owner.craftRunner().updateSleepiness();
        return true;
    }

    // ------------------------------------------------------------------
    // 恢复被中断的合成
    // ------------------------------------------------------------------

    /** 有 level 能读核心之后才收尾被存档打断的合成；{@code loadAdditional} 里方块实体还没有 level。 */
    void recoverInterruptedCraft() {
        if (owner.getLevel() == null || owner.getLevel().isClientSide()) {
            return;
        }
        // 产物槽是驱动方：只有 finishCraft 会清空它。那里有产物，就是上次合成没完成。
        ItemStack waiting = owner.inventory.getItem(BlockEntityArcaneAssembler.TARGET_SLOT);
        if (!waiting.isEmpty()) {
            owner.craft.setCrafting(true);
            // 只为预览网格恢复；价格和晶体已随合成存进盘里。
            ThEArcanePattern recovered = patternForResult(waiting);
            owner.craft.setCurrentPattern(recovered);
            if (recovered != null) {
                // 样板能读就重报这两个数，读不出才用存档里的。
                owner.craft.setCraftPrice(craftCost(recovered));
                owner.craft.setCraftCrystals(crystalStacksOf(recovered));
            }
            ThELog.LOG.info(
                    "[assembler] at {} resumed the craft a save interrupted: {} for {} vis{}",
                    owner.getBlockPos(),
                    waiting.getHoverName().getString(),
                    owner.craft.craftPrice(),
                    recovered == null ? " (the knowledge core no longer has its pattern)" : "");
            // 第一个 tick 就交付：合成时间在存档前已经走完。
            owner.craft.setCraftTicks(owner.upgrades.ticksPerCraft());
            owner.craft.clearStall();
        } else {
            owner.craft.setCrafting(false);
            owner.craft.setCraftTicks(0);
            owner.craft.setCraftPrice(0);
            owner.craft.setCraftCrystals(List.of());
            owner.displaySync.clearDisplay(true);
        }
        // 主动请求被 tick，不等后续的网格事件；还没有网格时是空操作。
        owner.craftRunner().updateSleepiness();
    }

    // ------------------------------------------------------------------
    // 接收任务
    // ------------------------------------------------------------------

    /** 机器完全接不了任务的原因，能接时为 {@code null}。
     * 两个入口点都先问这里，[AE2] 从哪条路进来都是同一句拒绝话。 */
    private @Nullable Component refusalFor() {
        if (!owner.acceptsPlans()) {
            return AssemblerStatus.refusalReason(AssemblerStatus.REFUSE_BUSY, "it is already holding a craft");
        }
        if (!owner.mainNode.isActive()) {
            return AssemblerStatus.refusalReason(
                    AssemblerStatus.REFUSE_NODE_INACTIVE, "its grid node is not active");
        }
        return null;
    }

    /** 接收同一网格上的样板供应器推来的任务；投入物只为归还而留。 */
    boolean accept(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        Component refusal = refusalFor();
        if (refusal != null) {
            noteRefusal(refusal);
            return false;
        }
        if (!(patternDetails instanceof ArcanePatternDetails details)) {
            noteRefusal(AssemblerStatus.refusalReason(
                    AssemblerStatus.REFUSE_NOT_ARCANE, "the pattern is not an arcane pattern this machine can read"));
            return false;
        }
        if (!canEverPay(details.pattern())) {
            noteRefusal(cannotPay(craftCost(details.pattern())));
            return false;
        }
        // [AE2] 刚抽走的物品。本机器用 vis 和晶体付账，这些只为归还而留。
        owner.craft.heldInputs().clear();
        for (KeyCounter counter : inputHolder) {
            for (var entry : counter) {
                if (entry.getKey() instanceof AEItemKey itemKey && entry.getLongValue() > 0) {
                    owner.craft.heldInputs()
                            .add(itemKey.toStack((int) Math.min(Integer.MAX_VALUE, entry.getLongValue())));
                }
            }
        }
        return beginCraft(details.pattern());
    }

    /** 接收同位置样板供应器推来的任务；投入物在这之前已被抽走。 */
    boolean acceptFromMachine(IPatternDetails patternDetails) {
        Component refusal = refusalFor();
        if (refusal != null) {
            noteRefusal(refusal);
            return false;
        }
        if (patternDetails instanceof ArcanePatternDetails details) {
            if (!canEverPay(details.pattern())) {
                noteRefusal(cannotPay(craftCost(details.pattern())));
                return false;
            }
            return beginCraft(details.pattern());
        }
        ThEArcanePattern resolved = resolveExternal(patternDetails);
        if (resolved == null) {
            noteRefusal(AssemblerStatus.refusalReason(
                    AssemblerStatus.REFUSE_UNRESOLVED, "the pattern does not resolve to an arcane recipe"));
            return false;
        }
        if (!canEverPay(resolved)) {
            noteRefusal(cannotPay(craftCost(resolved)));
            return false;
        }
        return beginCraft(resolved);
    }
}
