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
 * 奥术组装机的合成，作为一个可被接收、定价和取回的对象：一次合成花多少、
 * 它欠哪些晶体、灵气是否可能付得起，以及被存档打断的那次会怎样。
 * 从 {@link BlockEntityArcaneAssembler} 拆出，后者保留物品栏、网格时钟和
 * 菜单读取的公开接口；{@link AssemblerCraftRunner} 运行本类接收的合成。
 */
final class AssemblerCraftJob {

    private static final float MIN_CONSUMPTION_MODIFIER = 0.1F;

    private final BlockEntityArcaneAssembler owner;

    AssemblerCraftJob(BlockEntityArcaneAssembler owner) {
        this.owner = owner;
    }

    /** 样板合成必须被交付的晶体，以将向网络请求的物品形式表示。 */
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

    /** 为 {@code pattern} 收取的 vis，已计入装备折扣，并以 Thaumaturge 自己的
     * {@code MIN_CONSUMPTION_MODIFIER} 为下限，所以已装备的组装机仍需支付一些。 */
    public int craftCost(ThEArcanePattern pattern) {
        float modifier = Math.max(1.0F - owner.upgrades.gearDiscount() / 100.0F, MIN_CONSUMPTION_MODIFIER);
        return Math.max(1, (int) Math.ceil(pattern.chargedVis() * modifier));
    }

    /** 灵气是否能支付 {@code pattern}。付不起的任务会被拒绝而非保留：合成 CPU
     * 会跳过忙碌的供应器，保留它就会卡住整个计划。灵气低时会等待——其基础值可以上升。 */
    boolean canEverPay(ThEArcanePattern pattern) {
        int capacity = owner.vis.auraCapacity();
        // 0 表示区块尚未初始化；中继点也算，它的 vis 位于节点中。
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
        // getString() 按服务端的语言解析；每个键都带英文回退。
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
            // 重建但不结算过期标志：在读取 level 之前读到的集合会变成最终结果。
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
        // 现在就固定，而不是完成时重算，这样合成在存档后不依赖核心也能继续：
        // 价格和晶体随样板一并交付。
        owner.craft.begin(pattern, craftCost(pattern), crystalStacksOf(pattern));
        owner.displaySync.refreshDisplaySlots(pattern.result().copy(), pattern.grid());
        owner.setChanged();
        owner.displaySync.markForUpdate();
        // 唤醒网格：实测中，休眠时推入的合成每秒只 tick 一次，而不是 20 次。
        ICraftingProvider.requestUpdate(owner.mainNode);
        owner.craftRunner().updateSleepiness();
        return true;
    }

    // ------------------------------------------------------------------
    // 恢复被中断的合成
    // ------------------------------------------------------------------

    /** 完成恢复被存档打断的合成，此时已有 level 可用它读取核心：
     * 不在 {@code loadAdditional} 中做，那里方块实体还没有 level。 */
    void recoverInterruptedCraft() {
        if (owner.getLevel() == null || owner.getLevel().isClientSide()) {
            return;
        }
        // 由产物槽驱动：只有 finishCraft 会清空它，所以那里有产物就意味着合成没有完成。
        ItemStack waiting = owner.inventory.getItem(BlockEntityArcaneAssembler.TARGET_SLOT);
        if (!waiting.isEmpty()) {
            owner.craft.setCrafting(true);
            // 仅为预览网格恢复：价格和晶体已随合成保存。
            ThEArcanePattern recovered = patternForResult(waiting);
            owner.craft.setCurrentPattern(recovered);
            if (recovered != null) {
                // 可读取的样板会重述两个数值；保存的数值是回退。
                owner.craft.setCraftPrice(craftCost(recovered));
                owner.craft.setCraftCrystals(crystalStacksOf(recovered));
            }
            ThELog.LOG.info(
                    "[assembler] at {} resumed the craft a save interrupted: {} for {} vis{}",
                    owner.getBlockPos(),
                    waiting.getHoverName().getString(),
                    owner.craft.craftPrice(),
                    recovered == null ? " (the knowledge core no longer has its pattern)" : "");
            // 第一个 tick 就交付：合成时间在存档前已经走过。
            owner.craft.setCraftTicks(owner.upgrades.ticksPerCraft());
            owner.craft.clearStall();
        } else {
            owner.craft.setCrafting(false);
            owner.craft.setCraftTicks(0);
            owner.craft.setCraftPrice(0);
            owner.craft.setCraftCrystals(List.of());
            owner.displaySync.clearDisplay(true);
        }
        // 主动请求被 tick，而不是假定后面会有网格事件：还没有网格时这是空操作。
        owner.craftRunner().updateSleepiness();
    }

    // ------------------------------------------------------------------
    // 接收任务
    // ------------------------------------------------------------------

    /** 机器为何完全无法接收任务，可以接收时为 {@code null}：两个入口点都先问
     * 这个，所以无论 AE2 从哪条路进来，拒绝的措辞都一样。 */
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

    /** 接收同一网格上的样板供应器推送的任务；投入物只为归还而保留。 */
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
        // AE2 刚提取的物品。本机器以 vis 和晶体支付，所以保留这些只为归还。
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

    /** 接收同位置样板供应器推送的任务；其投入物在此刻之前已被提取。 */
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
