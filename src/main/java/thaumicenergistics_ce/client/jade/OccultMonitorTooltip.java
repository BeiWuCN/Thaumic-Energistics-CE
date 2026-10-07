package thaumicenergistics_ce.client.jade;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IElementHelper;
import thaumicenergistics_ce.infusion.InfusionRisk;
import thaumicenergistics_ce.integration.jade.OccultMonitorProvider;
import thaumicenergistics_ce.integration.jade.JadeGridState;

/**
 * 神秘监控器的 Jade tooltip：{@link OccultMonitorProvider} 的绘制半边。
 * 数字来自服务端，文字在这里拼，跟随玩家语言。
 * 风险拆成“4（基础 1 + 祭坛 3）”，玩家才知道该修哪一半。
 */
public final class OccultMonitorTooltip implements IBlockComponentProvider {

    public static final OccultMonitorTooltip INSTANCE = new OccultMonitorTooltip();

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag tag = accessor.getServerData();
        if (!tag.contains(JadeGridState.TAG)) {
            return;
        }
        IElementHelper helper = IElementHelper.get();
        JadeGridState state = JadeGridState.read(tag);
        tooltip.add(helper.text(state.label().copy().withStyle(state.colour())));

        // 状态行背后有两种故障：缺书是机器自身的毛病，玩家能修。
        // “没有祭坛”是对房间的判断，只有搜索才能下。
        if (!tag.getBoolean(OccultMonitorProvider.TAG_HAS_BOOK)) {
            tooltip.add(helper.text(Component.translatable("thaumicenergistics_ce.jade.monitor.no_book")
                    .withStyle(ChatFormatting.GOLD)));
        }
        if (!tag.getBoolean(OccultMonitorProvider.TAG_REPORTING)) {
            if (tag.getBoolean(OccultMonitorProvider.TAG_SEARCHED)
                    && !tag.getBoolean(OccultMonitorProvider.TAG_FOUND_ALTAR)) {
                tooltip.add(helper.text(Component.translatable("thaumicenergistics_ce.jade.monitor.no_matrix")
                        .withStyle(ChatFormatting.GRAY)));
            }
            return;
        }

        int tier = Math.max(1, Math.min(InfusionRisk.MAX_TIER, tag.getInt(OccultMonitorProvider.TAG_TIER)));
        tooltip.add(helper.text(Component.translatable(
                        "thaumicenergistics_ce.jade.monitor.tier",
                        Component.translatable("thaumicenergistics_ce.jade.monitor.risk." + tier),
                        tier)
                .withStyle(colourOf(tier))));
        // 先显示实时稳定性，那个数字才在变，仪式背后的两个排在后面。
        // 负值不是错误：祭坛把值夹在 -100 到 25 之间，低于零就吐出去。
        float stability = tag.getInt(OccultMonitorProvider.TAG_STABILITY) / 10.0F;
        tooltip.add(helper.text(Component.translatable(
                "thaumicenergistics_ce.jade.monitor.stability",
                String.format("%.1f", stability),
                Component.translatable("gui.thaumaturge.infusion.stability." + tierKeyOf(stability)))));
        tooltip.add(helper.text(Component.translatable(
                        "thaumicenergistics_ce.jade.monitor.instability",
                        tag.getInt(OccultMonitorProvider.TAG_BASE)
                                + tag.getInt(OccultMonitorProvider.TAG_ALTAR),
                        tag.getInt(OccultMonitorProvider.TAG_BASE),
                        tag.getInt(OccultMonitorProvider.TAG_ALTAR))
                .withStyle(ChatFormatting.GRAY)));
        tooltip.add(helper.text(Component.translatable("thaumicenergistics_ce.jade.monitor.tier." + tier)
                .withStyle(ChatFormatting.GRAY)));

        if (tag.getBoolean(OccultMonitorProvider.TAG_CRAFTING)) {
            tooltip.add(helper.text(
                    Component.translatable("thaumicenergistics_ce.jade.monitor.crafting")
                            .withStyle(ChatFormatting.WHITE)));
        }

        // 在客户端构建，名称用玩家的语言。
        ListTag wanted = tag.getList(OccultMonitorProvider.TAG_WANTED, Tag.TAG_STRING);
        if (!wanted.isEmpty()) {
            List<Component> names = new ArrayList<>();
            for (int i = 0; i < wanted.size(); i++) {
                names.add(Component.translatable("aspect.thaumaturge." + wanted.getString(i)));
            }
            tooltip.add(helper.text(Component.translatable(
                            "thaumicenergistics_ce.jade.monitor.shortages", join(names))
                    .withStyle(ChatFormatting.YELLOW)));
        } else {
            tooltip.add(helper.text(Component.translatable("thaumicenergistics_ce.jade.monitor.essence_ok")
                    .withStyle(ChatFormatting.GREEN)));
        }
    }

    /** Thaumaturge 自己给稳定度的档位说法，抄它的阈值和翻译键，护目镜与监控器才对得上。
     * 阈值出自 {@code BlockEntityInfusionMatrix.stabilityTierKey}。 */
    private static String tierKeyOf(float stability) {
        if (stability > 12.5F) {
            return "very_stable";
        }
        if (stability >= 0.0F) {
            return "stable";
        }
        return stability > -25.0F ? "unstable" : "very_unstable";
    }

    private static ChatFormatting colourOf(int tier) {
        return switch (tier) {
            case 1 -> ChatFormatting.GREEN;
            case 2 -> ChatFormatting.DARK_GREEN;
            case 3 -> ChatFormatting.YELLOW;
            case 4 -> ChatFormatting.GOLD;
            default -> ChatFormatting.RED;
        };
    }

    private static Component join(List<Component> parts) {
        MutableComponent joined = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                joined.append(Component.literal(", "));
            }
            joined.append(parts.get(i));
        }
        return joined;
    }

    @Override
    public ResourceLocation getUid() {
        return OccultMonitorProvider.UID;
    }
}
