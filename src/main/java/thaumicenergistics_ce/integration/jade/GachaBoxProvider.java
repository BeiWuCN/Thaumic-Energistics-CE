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

public class GachaBoxProvider implements IServerDataProvider<BlockAccessor> {

    public static final GachaBoxProvider INSTANCE = new GachaBoxProvider();

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
        tag.putBoolean(TAG_ONLINE, box.getMainNode().isActive());
        String owner = box.ownerName();
        if (owner != null) {
            tag.putString(TAG_OWNER, owner);
        }
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
