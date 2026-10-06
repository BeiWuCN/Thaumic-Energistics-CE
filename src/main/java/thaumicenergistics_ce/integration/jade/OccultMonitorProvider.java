package thaumicenergistics_ce.integration.jade;

import appeng.api.networking.IGridNode;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.blockentity.occultmonitor.BlockEntityOccultMonitor;
import thaumicenergistics_ce.infusion.InfusionRisk;

/**
 * The Occult Monitor's Jade server data: whether it can see, and what it sees.
 * <ul>
 *   <li>The server reads the altar and writes the answers into the data tag; stability is server-side.
 *   <li>The drawing half is {@code client.jade.OccultMonitorTooltip}, paired by {@link #UID}. The
 *       numbers travel raw so that half can put them in the player's own words.
 * </ul>
 */
public class OccultMonitorProvider implements IServerDataProvider<BlockAccessor> {

    public static final OccultMonitorProvider INSTANCE = new OccultMonitorProvider();

    /** Shared with {@code client.jade.OccultMonitorTooltip}: Jade pairs the two halves by UID. */
    public static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "occult_monitor");

    /** Whether the monitor has its book and an altar - without both it says nothing about risk.
     * The bubble the machine syncs to its own renderer spells this word too
     * ({@code OccultMonitorSync.TAG_REPORTING}); the two are separate documents, so
     * {@code SyncSelfTest} asserts they stay equal. */
    public static final String TAG_REPORTING = "Reporting";
    public static final String TAG_FOUND_ALTAR = "FoundAltar";
    /** Whether an altar search has run since the monitor's node was last active. "No altar" is a fact
     * about the room only once the room was searched. */
    public static final String TAG_SEARCHED = "Searched";
    /** Whether the Thaumonomicon is on the machine. Without it the monitor is blind, not idle. */
    public static final String TAG_HAS_BOOK = "HasBook";
    public static final String TAG_CRAFTING = "Crafting";
    public static final String TAG_TIER = "Tier";
    public static final String TAG_BASE = "BaseInstability";
    public static final String TAG_ALTAR = "AltarInstability";
    /** The altar's live stability, times ten. See {@link #appendServerData}. */
    public static final String TAG_STABILITY = "Stability";
    public static final String TAG_WANTED = "Wanted";

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof BlockEntityOccultMonitor monitor)) {
            return;
        }
        IGridNode node = monitor.getActionableNode();
        JadeGridState.of(node).write(tag, node);

        tag.putBoolean(TAG_REPORTING, monitor.canReport());
        BlockEntityOccultMonitor.Report report = monitor.report();
        tag.putBoolean(TAG_FOUND_ALTAR, report.foundAltar());
        tag.putBoolean(TAG_SEARCHED, monitor.hasSearchedAltar());
        tag.putBoolean(TAG_HAS_BOOK, monitor.hasBook());
        tag.putBoolean(TAG_CRAFTING, report.crafting());

        InfusionRisk risk = monitor.risk();
        tag.putInt(TAG_TIER, risk.tier());
        tag.putInt(TAG_BASE, risk.base());
        tag.putInt(TAG_ALTAR, risk.altar());
        // The altar's own stability, times ten: it is a float and the tag carries ints.
        tag.putInt(TAG_STABILITY, Math.round(risk.stability() * 10.0F));

        ListTag wanted = new ListTag();
        for (AspectInstance entry : report.remaining().entries()) {
            if (entry.amount() > 0) {
                entry.aspect().unwrapKey()
                        .ifPresent(key -> wanted.add(StringTag.valueOf(key.location().getPath())));
            }
        }
        tag.put(TAG_WANTED, wanted);
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
