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
 * The Occult Monitor's Jade tooltip: the drawing half of {@link OccultMonitorProvider}.
 * The raw numbers come from the server, but the words are built here, so they follow the player's
 * language rather than the server's. Risk is split as "4 (base 1 + altar 3)", so the player knows
 * which half to fix.
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

        // The two faults behind the state line: a missing book is the machine's own fault and the one a
        // player can fix, while "no altar" is a claim about the room that only a search may make.
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
        // The live stability first, because it is the number that moves, then the two behind the ritual.
        // Negative is not an error: the altar clamps from -100 to 25 and throws things below zero.
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

        // Built here on the client, so the names come out in the player's language rather than the server's.
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

    /** Thaumaturge's own word for a stability, copied from its thresholds and keys so the goggles and the
     * monitor agree. The thresholds are {@code BlockEntityInfusionMatrix.stabilityTierKey}'s. */
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
