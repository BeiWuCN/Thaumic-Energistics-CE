package thaumicenergistics_ce.blockentity.occultmonitor;

import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import thaumicenergistics_ce.blockentity.ClientSyncSend;
import thaumicenergistics_ce.infusion.InfusionRisk;
import thaumicenergistics_ce.util.ThEItemTags;

/** 神秘监控器客户端侧的气泡副本，从 {@link BlockEntityOccultMonitor} 拆出。
 * 渲染器读副本，只在有数据包发出时替换，一次渲染不会看到半个气泡；
 * 祭坛的实时数字与副本比较，决定是否发这个数据包。 */
final class OccultMonitorSync {

    /** 气泡的线上名称。Jade 载荷在自己的契约里写出第一个名字，
     * 即 {@code OccultMonitorProvider.TAG_REPORTING}；两个名字得一致。 */
    static final String TAG_REPORTING = "Reporting";
    static final String TAG_TIER = "BubbleTier";
    static final String TAG_INSTABILITY = "BubbleInstability";
    static final String TAG_STABILITY = "BubbleStability";
    static final String TAG_CRAFTING = "BubbleCrafting";
    static final String TAG_CRAFT = "BubbleCraft";
    static final String TAG_ESSENTIA = "BubbleEssentia";

    /** {@link #TAG_ESSENTIA} 一行之内的三个名称。 */
    private static final String TAG_ASPECT = "Aspect";
    private static final String TAG_DRAWN = "Drawn";
    private static final String TAG_TOTAL = "Total";

    private boolean reporting;
    private int tier = 1;
    private int instability;
    private int stabilityTimesTen = 250;
    private boolean crafting;
    private ItemStack craft = ItemStack.EMPTY;
    private final List<BlockEntityOccultMonitor.EssentiaLine> essentia = new ArrayList<>();

    private boolean sentReporting;
    private int sentTier = -1;
    private int sentInstability = -1;
    private String sentSignature = "";

    /** 一次祭坛读数，由监控器交付。 */
    record Snapshot(
            boolean reporting,
            int tier,
            int instability,
            int stabilityTimesTen,
            boolean crafting,
            ItemStack craft,
            List<BlockEntityOccultMonitor.EssentiaLine> lines) {}

    boolean reporting() {
        return reporting;
    }

    int tier() {
        return tier;
    }

    int instability() {
        return instability;
    }

    int stabilityTimesTen() {
        return stabilityTimesTen;
    }

    boolean crafting() {
        return crafting;
    }

    ItemStack craft() {
        return craft;
    }

    List<BlockEntityOccultMonitor.EssentiaLine> essentia() {
        return List.copyOf(essentia);
    }

    /** 收下一次读数，与上次发送的不同才发。 */
    void offer(BlockEntity owner, Snapshot reading) {
        // 合成期间稳定性会变，它算签名的一部分，否则气泡会冻结。
        String signature = reading.crafting() + "|" + reading.craft().getItem() + "|" + reading.lines()
                + "|" + reading.stabilityTimesTen();
        if (reading.reporting() == sentReporting
                && reading.tier() == sentTier
                && reading.instability() == sentInstability
                && signature.equals(sentSignature)) {
            return;
        }
        sentReporting = reading.reporting();
        sentTier = reading.tier();
        sentInstability = reading.instability();
        sentSignature = signature;
        reporting = reading.reporting();
        tier = reading.tier();
        instability = reading.instability();
        stabilityTimesTen = reading.stabilityTimesTen();
        crafting = reading.crafting();
        craft = reading.craft();
        essentia.clear();
        essentia.addAll(reading.lines());
        ClientSyncSend.sendBlockEntityUpdate(owner);
    }

    /** 写更新标签里气泡的那一半。书不在这里，它作为方块状态传输。 */
    void write(ValueOutput output) {
        output.putBoolean(TAG_REPORTING, reporting);
        output.putInt(TAG_TIER, tier);
        output.putInt(TAG_INSTABILITY, instability);
        output.putInt(TAG_STABILITY, stabilityTimesTen);
        output.putBoolean(TAG_CRAFTING, crafting);
        // 空的合成物品按空复合标签发送，不发成缺失的键：渲染器要读这个字段，
        // 而读取方把缺失当成「写入方无话可说」，那是另一回事。
        output.store(TAG_CRAFT, ItemStack.OPTIONAL_CODEC, craft);
        ValueOutput.ValueOutputList lines = output.childrenList(TAG_ESSENTIA);
        for (BlockEntityOccultMonitor.EssentiaLine line : essentia) {
            ValueOutput entry = lines.addChild();
            entry.putString(TAG_ASPECT, line.aspect());
            entry.putInt(TAG_DRAWN, line.drawn());
            entry.putInt(TAG_TOTAL, line.total());
        }
    }

    /** 把更新标签里气泡的那一半读回渲染器画的副本。 */
    void read(ValueInput input) {
        reporting = input.getBooleanOr(TAG_REPORTING, false);
        tier = Math.max(1, Math.min(InfusionRisk.MAX_TIER, input.getIntOr(TAG_TIER, 0)));
        instability = input.getIntOr(TAG_INSTABILITY, 0);
        stabilityTimesTen = input.getIntOr(TAG_STABILITY, 0);
        crafting = input.getBooleanOr(TAG_CRAFTING, false);
        craft = input.read(TAG_CRAFT, ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        essentia.clear();
        for (ValueInput entry : input.childrenListOrEmpty(TAG_ESSENTIA)) {
            essentia.add(new BlockEntityOccultMonitor.EssentiaLine(
                    entry.getStringOr(TAG_ASPECT, ""), entry.getIntOr(TAG_DRAWN, 0), entry.getIntOr(TAG_TOTAL, 0)));
        }
    }
}
