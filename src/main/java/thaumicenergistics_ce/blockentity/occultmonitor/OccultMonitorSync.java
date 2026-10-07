package thaumicenergistics_ce.blockentity.occultmonitor;

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

/** 神秘监控器客户端侧的气泡副本，从 {@link BlockEntityOccultMonitor} 中拆出：
 * 气泡渲染器绘制的内容、祭坛最后一次交付的内容，以及两端都读取的标签。
 * 渲染器读的是副本，只在有数据包发出时才替换，所以一次渲染不会看到半个
 * 气泡；祭坛的实时数字与副本比较，以决定是否发出该数据包。 */
final class OccultMonitorSync {

    /** 气泡的线上名称。Jade 载荷在它自己的契约里写出第一个名字，即
     * {@code OccultMonitorProvider.TAG_REPORTING}；两者成对，必须保持一致。 */
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

    /** 接收一次读数，并在它与上次发送的不同时发送它。 */
    void offer(BlockEntity owner, Snapshot reading) {
        // 合成期间稳定性会变化，所以它属于签名的一部分，否则气泡会冻结。
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

    /** 写入更新标签中气泡的那一半。书不在这里——它作为方块状态传输。 */
    void write(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean(TAG_REPORTING, reporting);
        tag.putInt(TAG_TIER, tier);
        tag.putInt(TAG_INSTABILITY, instability);
        tag.putInt(TAG_STABILITY, stabilityTimesTen);
        tag.putBoolean(TAG_CRAFTING, crafting);
        tag.put(TAG_CRAFT, craft.saveOptional(registries));
        ListTag lines = new ListTag();
        for (BlockEntityOccultMonitor.EssentiaLine line : essentia) {
            CompoundTag entry = new CompoundTag();
            entry.putString(TAG_ASPECT, line.aspect());
            entry.putInt(TAG_DRAWN, line.drawn());
            entry.putInt(TAG_TOTAL, line.total());
            lines.add(entry);
        }
        tag.put(TAG_ESSENTIA, lines);
    }

    /** 把更新标签中气泡的那一半读回渲染器绘制的副本。 */
    void read(CompoundTag tag, HolderLookup.Provider registries) {
        reporting = tag.getBoolean(TAG_REPORTING);
        tier = Math.max(1, Math.min(InfusionRisk.MAX_TIER, tag.getInt(TAG_TIER)));
        instability = tag.getInt(TAG_INSTABILITY);
        stabilityTimesTen = tag.getInt(TAG_STABILITY);
        crafting = tag.getBoolean(TAG_CRAFTING);
        craft = ItemStack.parseOptional(registries, tag.getCompound(TAG_CRAFT));
        essentia.clear();
        ListTag lines = tag.getList(TAG_ESSENTIA, Tag.TAG_COMPOUND);
        for (int i = 0; i < lines.size(); i++) {
            CompoundTag entry = lines.getCompound(i);
            essentia.add(new BlockEntityOccultMonitor.EssentiaLine(
                    entry.getString(TAG_ASPECT), entry.getInt(TAG_DRAWN), entry.getInt(TAG_TOTAL)));
        }
    }
}
