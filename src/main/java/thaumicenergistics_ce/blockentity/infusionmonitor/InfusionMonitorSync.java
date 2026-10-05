package thaumicenergistics_ce.blockentity.infusionmonitor;

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

/** The infusion monitor's client copy of the bubble, split out of {@link BlockEntityInfusionMonitor}:
 * what the bubble renderer draws, what the altar last handed over, and the tag both sides read. The
 * renderer reads a copy, replaced only when a packet goes out, so a render pass never sees half a
 * bubble; the altar's live numbers are compared against the copy to decide on that packet. */
final class InfusionMonitorSync {

    /** The bubble's wire names. The Jade payload spells the first one in its own contract
     * ({@code InfusionMonitorProvider.TAG_REPORTING}); the sync self-test holds the two equal. */
    static final String TAG_REPORTING = "Reporting";
    static final String TAG_TIER = "BubbleTier";
    static final String TAG_INSTABILITY = "BubbleInstability";
    static final String TAG_STABILITY = "BubbleStability";
    static final String TAG_CRAFTING = "BubbleCrafting";
    static final String TAG_CRAFT = "BubbleCraft";
    static final String TAG_ESSENTIA = "BubbleEssentia";

    /** The three names inside one line of {@link #TAG_ESSENTIA}. */
    private static final String TAG_ASPECT = "Aspect";
    private static final String TAG_DRAWN = "Drawn";
    private static final String TAG_TOTAL = "Total";

    private boolean reporting;
    private int tier = 1;
    private int instability;
    private int stabilityTimesTen = 250;
    private boolean crafting;
    private ItemStack craft = ItemStack.EMPTY;
    private final List<BlockEntityInfusionMonitor.EssentiaLine> essentia = new ArrayList<>();

    private boolean sentReporting;
    private int sentTier = -1;
    private int sentInstability = -1;
    private String sentSignature = "";

    /** One reading of the altar, as handed over by the monitor. */
    record Snapshot(
            boolean reporting,
            int tier,
            int instability,
            int stabilityTimesTen,
            boolean crafting,
            ItemStack craft,
            List<BlockEntityInfusionMonitor.EssentiaLine> lines) {}

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

    List<BlockEntityInfusionMonitor.EssentiaLine> essentia() {
        return List.copyOf(essentia);
    }

    /** Takes a reading and sends it when it differs from the last one sent. */
    void offer(BlockEntity owner, Snapshot reading) {
        // Stability moves during a craft, so it is part of the signature or the bubble would freeze.
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

    /** Writes the bubble half of the update tag. The book is not here - it travels as a blockstate. */
    void write(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean(TAG_REPORTING, reporting);
        tag.putInt(TAG_TIER, tier);
        tag.putInt(TAG_INSTABILITY, instability);
        tag.putInt(TAG_STABILITY, stabilityTimesTen);
        tag.putBoolean(TAG_CRAFTING, crafting);
        tag.put(TAG_CRAFT, craft.saveOptional(registries));
        ListTag lines = new ListTag();
        for (BlockEntityInfusionMonitor.EssentiaLine line : essentia) {
            CompoundTag entry = new CompoundTag();
            entry.putString(TAG_ASPECT, line.aspect());
            entry.putInt(TAG_DRAWN, line.drawn());
            entry.putInt(TAG_TOTAL, line.total());
            lines.add(entry);
        }
        tag.put(TAG_ESSENTIA, lines);
    }

    /** Reads the bubble half of the update tag back into the copy the renderer draws. */
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
            essentia.add(new BlockEntityInfusionMonitor.EssentiaLine(
                    entry.getString(TAG_ASPECT), entry.getInt(TAG_DRAWN), entry.getInt(TAG_TOTAL)));
        }
    }
}
