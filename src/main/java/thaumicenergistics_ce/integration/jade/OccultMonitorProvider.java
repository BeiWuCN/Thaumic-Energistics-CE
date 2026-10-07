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
 * 神秘监控器的 Jade 服务端数据：它看不看得见，看见了什么。
 * 服务端读祭坛并把结果写进数据标签；稳定性归服务端。
 * 绘制那一半是 [client.jade.OccultMonitorTooltip]，按 [UID] 配对，数字原样传过去。
 */
public class OccultMonitorProvider implements IServerDataProvider<BlockAccessor> {

    public static final OccultMonitorProvider INSTANCE = new OccultMonitorProvider();

    /** 与 [client.jade.OccultMonitorTooltip] 共用：Jade 按 [UID] 配对这两半。 */
    public static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "occult_monitor");

    /** 监控器是否备有书和祭坛：缺一个它就不出风险结论。
     * 机器同步给自己渲染器的气泡也写这个词（[OccultMonitorSync.TAG_REPORTING]）；两处得一致。 */
    public static final String TAG_REPORTING = "Reporting";
    public static final String TAG_FOUND_ALTAR = "FoundAltar";
    /** 节点的上次活跃之后有没有跑过祭坛搜索。
     * 「没有祭坛」在房间被搜过之后才是关于这个房间的事实。 */
    public static final String TAG_SEARCHED = "Searched";
    /** 机器上装没装神秘学书（thaumonomicon）。缺了它监控器是瞎的，不是空闲。 */
    public static final String TAG_HAS_BOOK = "HasBook";
    public static final String TAG_CRAFTING = "Crafting";
    public static final String TAG_TIER = "Tier";
    public static final String TAG_BASE = "BaseInstability";
    public static final String TAG_ALTAR = "AltarInstability";
    /** 祭坛当前稳定性乘十后的值。见 [appendServerData]。 */
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
        // 祭坛自己的稳定性乘十：它是 float，这标签只能装 int。
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
