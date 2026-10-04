package thaumicenergistics_ce.integration.jade;

import appeng.api.networking.IGridNode;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.blockentity.BlockEntityInfusionMonitor;
import thaumicenergistics_ce.infusion.InfusionRisk;

/**
 * The Infusion Monitor's Jade tooltip: whether it can see, and what it sees.
 * <ul>
 *   <li>The server reads the altar and writes the answers into the data tag; stability is server-side.
 *   <li>The network line comes first and is always there: a monitor off the network watches nothing.
 *   <li>Risk is split as "4 (base 1 + altar 3)" so the player knows which half to fix.
 * </ul>
 */
public class InfusionMonitorProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    public static final InfusionMonitorProvider INSTANCE = new InfusionMonitorProvider();

    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "infusion_monitor");

    /** Whether the monitor has its book and has found an altar - without both it says nothing about risk. */
    private static final String TAG_REPORTING = "Reporting";
    private static final String TAG_FOUND_ALTAR = "FoundAltar";
    /** Whether the Thaumonomicon is on the machine. Without it the monitor is blind, not idle. */
    private static final String TAG_HAS_BOOK = "HasBook";
    private static final String TAG_CRAFTING = "Crafting";
    /** The risk, as the tier and the two numbers behind it. */
    private static final String TAG_TIER = "Tier";
    private static final String TAG_BASE = "BaseInstability";
    private static final String TAG_ALTAR = "AltarInstability";
    /** The altar's live stability, times ten. See {@link #appendServerData}. */
    private static final String TAG_STABILITY = "Stability";
    /** The aspects the ritual is still waiting for, by id path. */
    private static final String TAG_WANTED = "Wanted";

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof BlockEntityInfusionMonitor monitor)) {
            return;
        }
        IGridNode node = monitor.getActionableNode();
        JadeGridState.of(node).write(tag, node);

        tag.putBoolean(TAG_REPORTING, monitor.canReport());
        BlockEntityInfusionMonitor.Report report = monitor.report();
        tag.putBoolean(TAG_FOUND_ALTAR, report.foundAltar());
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
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag tag = accessor.getServerData();
        if (!tag.contains(JadeGridState.TAG)) {
            return;
        }
        var helper = snownee.jade.api.ui.IElementHelper.get();
        JadeGridState state = JadeGridState.read(tag);
        tooltip.add(helper.text(state.label().copy().withStyle(state.colour())));

        if (!tag.getBoolean(TAG_REPORTING)) {
            // Two faults, two sentences: no altar is a fact about the room, no book is about the machine and
            // is the one a player can fix. Reporting both as "no altar" sent readers hunting the wrong block.
            if (!tag.getBoolean(TAG_FOUND_ALTAR)) {
                tooltip.add(helper.text(Component.translatable("thaumicenergistics_ce.jade.monitor.no_matrix")
                        .withStyle(ChatFormatting.GRAY)));
            } else if (!tag.getBoolean(TAG_HAS_BOOK)) {
                tooltip.add(helper.text(Component.translatable("thaumicenergistics_ce.jade.monitor.no_book")
                        .withStyle(ChatFormatting.GOLD)));
            }
            return;
        }

        int tier = Math.max(1, Math.min(InfusionRisk.MAX_TIER, tag.getInt(TAG_TIER)));
        tooltip.add(helper.text(Component.translatable(
                        "thaumicenergistics_ce.jade.monitor.tier",
                        Component.translatable("thaumicenergistics_ce.jade.monitor.risk." + tier),
                        tier)
                .withStyle(colourOf(tier))));
        // The live stability first, because it is the number that moves, then the two behind the ritual.
        // Negative is not an error: the altar clamps from -100 to 25 and throws things below zero.
        float stability = tag.getInt(TAG_STABILITY) / 10.0F;
        tooltip.add(helper.text(Component.translatable(
                "thaumicenergistics_ce.jade.monitor.stability",
                String.format("%.1f", stability),
                Component.translatable("gui.thaumaturge.infusion.stability." + tierKeyOf(stability)))));
        tooltip.add(helper.text(Component.translatable(
                        "thaumicenergistics_ce.jade.monitor.instability",
                        tag.getInt(TAG_BASE) + tag.getInt(TAG_ALTAR),
                        tag.getInt(TAG_BASE),
                        tag.getInt(TAG_ALTAR))
                .withStyle(ChatFormatting.GRAY)));
        tooltip.add(helper.text(Component.translatable("thaumicenergistics_ce.jade.monitor.tier." + tier)
                .withStyle(ChatFormatting.GRAY)));

        if (tag.getBoolean(TAG_CRAFTING)) {
            tooltip.add(helper.text(
                    Component.translatable("thaumicenergistics_ce.jade.monitor.crafting")
                            .withStyle(ChatFormatting.WHITE)));
        }

        // Built here on the client, so the names come out in the player's language rather than the server's.
        ListTag wanted = tag.getList(TAG_WANTED, Tag.TAG_STRING);
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

    /** The five tiers, from reassuring to "do not start this". */
    private static ChatFormatting colourOf(int tier) {
        return switch (tier) {
            case 1 -> ChatFormatting.GREEN;
            case 2 -> ChatFormatting.DARK_GREEN;
            case 3 -> ChatFormatting.YELLOW;
            case 4 -> ChatFormatting.GOLD;
            default -> ChatFormatting.RED;
        };
    }

    /** "Ignis, Terra", as one component. */
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
        return UID;
    }
}
