package thaumicenergistics_ce.client.jade;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IElement;
import snownee.jade.api.ui.IElementHelper;
import thaumicenergistics_ce.integration.jade.GachaBoxProvider;

/** The gacha box's drawing half: what it is bound to, and why it is standing still. */
public final class GachaBoxTooltip implements IBlockComponentProvider {

    public static final GachaBoxTooltip INSTANCE = new GachaBoxTooltip();

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag tag = accessor.getServerData();
        // The node's state leads: whether the box is on the network is the first thing worth knowing,
        // and it is worded the way AE2 words it for its own devices.
        if (tag.contains(GachaBoxProvider.TAG_ONLINE)) {
            boolean online = tag.getBoolean(GachaBoxProvider.TAG_ONLINE);
            tooltip.add(IElementHelper.get()
                    .text(Component
                            .translatable(online
                                    ? "jade.thaumicenergistics_ce.gacha_box.online"
                                    : "jade.thaumicenergistics_ce.gacha_box.offline")
                            .withStyle(online ? ChatFormatting.GREEN : ChatFormatting.RED)));
        }
        if (tag.getBoolean(GachaBoxProvider.TAG_INCOMPLETE)) {
            tooltip.add(IElementHelper.get()
                    .text(Component.translatable("jade.thaumicenergistics_ce.gacha_box.incomplete")
                            .withStyle(ChatFormatting.RED)));
        }
        Component wait = decode(tag.get(GachaBoxProvider.TAG_WAIT));
        if (wait != null) {
            tooltip.add(IElementHelper.get().text(wait.copy().withStyle(ChatFormatting.RED)));
        }
        if (tag.contains(GachaBoxProvider.TAG_OWNER)) {
            // The name is the part worth reading at a glance, so it is the part that gets a colour.
            Component name = Component.literal(tag.getString(GachaBoxProvider.TAG_OWNER))
                    .withStyle(ChatFormatting.GOLD);
            tooltip.add(IElementHelper.get().text(Component
                    .translatable("jade.thaumicenergistics_ce.gacha_box.bound", name)
                    .withStyle(ChatFormatting.GRAY)));
        }
        // The cards draw themselves: an icon says "which card, how many" at a glance, where the count
        // sentence had to be read. No label, because the icons are the label.
        ListTag cards = tag.getList(GachaBoxProvider.TAG_CARDS, Tag.TAG_COMPOUND);
        var level = accessor.getLevel();
        if (level != null) {
            var registries = level.registryAccess();
            List<IElement> row = new ArrayList<>();
            for (int i = 0; i < cards.size(); i++) {
                ItemStack card = ItemStack.parseOptional(registries, cards.getCompound(i));
                if (!card.isEmpty()) {
                    row.add(IElementHelper.get().item(card));
                }
            }
            if (!row.isEmpty()) {
                tooltip.add(row);
            }
        }
    }

    private static @Nullable Component decode(@Nullable Tag encoded) {
        if (encoded == null) {
            return null;
        }
        return ComponentSerialization.CODEC.parse(NbtOps.INSTANCE, encoded).result().orElse(null);
    }

    @Override
    public ResourceLocation getUid() {
        return GachaBoxProvider.UID;
    }
}
