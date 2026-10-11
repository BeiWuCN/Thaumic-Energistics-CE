package thaumicenergistics_ce.integration.jade;

import appeng.api.networking.IGridNode;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.blockentity.occultmonitor.BlockEntityOccultMonitor;
import thaumicenergistics_ce.recipe.infusion.InfusionRisk;

public class OccultMonitorProvider implements IServerDataProvider<BlockAccessor> {

    public static final OccultMonitorProvider INSTANCE = new OccultMonitorProvider();

    public static final Identifier UID =
            Identifier.fromNamespaceAndPath(ThEIds.MODID, "occult_monitor");

    public static final String TAG_REPORTING = "Reporting";
    public static final String TAG_FOUND_ALTAR = "FoundAltar";
    public static final String TAG_SEARCHED = "Searched";
    public static final String TAG_HAS_BOOK = "HasBook";
    public static final String TAG_CRAFTING = "Crafting";
    public static final String TAG_TIER = "Tier";
    public static final String TAG_BASE = "BaseInstability";
    public static final String TAG_ALTAR = "AltarInstability";
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
        tag.putInt(TAG_STABILITY, Math.round(risk.stability() * 10.0F));

        ListTag wanted = new ListTag();
        for (AspectInstance entry : report.remaining().entries()) {
            if (entry.amount() > 0) {
                entry.aspect().unwrapKey()
                        .ifPresent(key -> wanted.add(StringTag.valueOf(key.identifier().getPath())));
            }
        }
        tag.put(TAG_WANTED, wanted);
    }

    @Override
    public Identifier getUid() {
        return UID;
    }
}
