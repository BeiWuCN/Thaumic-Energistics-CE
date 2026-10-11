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

final class OccultMonitorSync {

    static final String TAG_REPORTING = "Reporting";
    static final String TAG_TIER = "BubbleTier";
    static final String TAG_INSTABILITY = "BubbleInstability";
    static final String TAG_STABILITY = "BubbleStability";
    static final String TAG_CRAFTING = "BubbleCrafting";
    static final String TAG_CRAFT = "BubbleCraft";
    static final String TAG_ESSENTIA = "BubbleEssentia";

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

    void offer(BlockEntity owner, Snapshot reading) {
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

    void write(ValueOutput output) {
        output.putBoolean(TAG_REPORTING, reporting);
        output.putInt(TAG_TIER, tier);
        output.putInt(TAG_INSTABILITY, instability);
        output.putInt(TAG_STABILITY, stabilityTimesTen);
        output.putBoolean(TAG_CRAFTING, crafting);
        output.store(TAG_CRAFT, ItemStack.OPTIONAL_CODEC, craft);
        ValueOutput.ValueOutputList lines = output.childrenList(TAG_ESSENTIA);
        for (BlockEntityOccultMonitor.EssentiaLine line : essentia) {
            ValueOutput entry = lines.addChild();
            entry.putString(TAG_ASPECT, line.aspect());
            entry.putInt(TAG_DRAWN, line.drawn());
            entry.putInt(TAG_TOTAL, line.total());
        }
    }

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
