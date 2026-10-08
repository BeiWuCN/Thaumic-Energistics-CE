package thaumicenergistics_ce.integration.jade;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.blockentity.gachabox.BlockEntityGachaBox;
import thaumicenergistics_ce.blockentity.gachabox.GachaWait;
import thaumicenergistics_ce.util.ThEItemTags;

/** Gacha Box 的服务端半边：它绑定到谁，以及它为何不转。 */
public class GachaBoxProvider implements IServerDataProvider<BlockAccessor> {

    public static final GachaBoxProvider INSTANCE = new GachaBoxProvider();

    /** 与 {@code client.jade.GachaBoxTooltip} 共用：Jade 靠 UID 配对两半。 */
    public static final Identifier UID = Identifier.fromNamespaceAndPath(ThEIds.MODID, "gacha_box");

    public static final String TAG_ONLINE = "Online";

    public static final String TAG_OWNER = "OwnerName";

    public static final String TAG_INCOMPLETE = "Incomplete";

    public static final String TAG_WAIT = "WaitReason";

    public static final String TAG_CARDS = "Cards";

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof BlockEntityGachaBox box)) {
            return;
        }
        if (!box.structureComplete()) {
            tag.putBoolean(TAG_INCOMPLETE, true);
        }
        // 放在 tooltip 最前：玩家最先要确认盒子有没有接入网络。
        // 这一条同时覆盖 "no power" 与 "no channel"。
        tag.putBoolean(TAG_ONLINE, box.getMainNode().isActive());
        String owner = box.ownerName();
        if (owner != null) {
            tag.putString(TAG_OWNER, owner);
        }
        // 按已保存的物品堆发送，不发数量：tooltip 要画出这些卡片，光有 id 画不出来。
        // 空槽位不发，这一行恰好是盒子里的卡片。
        ListTag cards = new ListTag();
        var level = accessor.getLevel();
        if (level != null) {
            var registries = level.registryAccess();
            for (ItemStack card : box.cards()) {
                if (!card.isEmpty()) {
                    cards.add(ThEItemTags.save(card, registries));
                }
            }
        }
        tag.put(TAG_CARDS, cards);
        // 原因作为组件传输，客户端用玩家自己的语言渲染。
        // 两种原因不传：缺失的上半部分会自己显现，节点离线上面已经说过。
        if (box.getLevel() instanceof ServerLevel server) {
            GachaWait wait = box.waitReason(server);
            if (wait != null && wait != GachaWait.NO_STRUCTURE && wait != GachaWait.NO_CHANNEL) {
                tag.put(TAG_WAIT, encode(wait.label()));
            }
        }
    }

    private static Tag encode(Component component) {
        return ComponentSerialization.CODEC
                .encodeStart(NbtOps.INSTANCE, component)
                .result()
                .orElse(new CompoundTag());
    }

    @Override
    public Identifier getUid() {
        return UID;
    }
}
