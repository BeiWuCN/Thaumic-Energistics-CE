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

/** 概率之箱的绘制半边：箱子绑定到了什么，以及为什么停着不动。 */
public final class GachaBoxTooltip implements IBlockComponentProvider {

    public static final GachaBoxTooltip INSTANCE = new GachaBoxTooltip();

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag tag = accessor.getServerData();
        // 节点状态打头：箱子在不在网络上是第一件值得知道的事，
        // 措辞沿用 AE2 描述自家设备的说法。
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
            // 名字是值得一眼读到的部分，上色的就是名字。
            Component name = Component.literal(tag.getString(GachaBoxProvider.TAG_OWNER))
                    .withStyle(ChatFormatting.GOLD);
            tooltip.add(IElementHelper.get().text(Component
                    .translatable("jade.thaumicenergistics_ce.gacha_box.bound", name)
                    .withStyle(ChatFormatting.GRAY)));
        }
        // 卡牌自己画：一个图标一眼说明「哪张卡、几张」，计数那句话还得读一遍。
        // 没有标签，图标就是标签。
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
