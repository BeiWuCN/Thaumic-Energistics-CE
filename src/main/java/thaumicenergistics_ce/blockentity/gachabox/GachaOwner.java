package thaumicenergistics_ce.blockentity.gachabox;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * 箱子绑定的那个玩家：脑由谁放入，一次转动的结果就归谁。绑定只有这一个字段，
 * 所以“这颗脑有主吗”和“这个玩家能不能拿走它”读的是同一处。未认领的脑不是
 * 缺失的脑，它是一次存档中断留下的东西，点一下箱子就能认领。
 */
final class GachaOwner {

    private static final String TAG_OWNER = "Owner";
    private static final String TAG_OWNER_NAME = "OwnerName";

    private @Nullable UUID uuid;
    private String name = "";

    boolean isBound() {
        return uuid != null;
    }

    /** 把箱子绑定到放入脑的那个人。 */
    void bind(Player player) {
        this.uuid = player.getUUID();
        this.name = player.getName().getString();
    }

    void unbind() {
        this.uuid = null;
        this.name = "";
    }

    /** 已绑定玩家的名字，箱子没有主人时为 null。 */
    @Nullable String name() {
        return uuid == null ? null : name;
    }

    /** 已绑定的玩家，现在在线时才会有；没有人在那里可以接收任何东西。 */
    @Nullable ServerPlayer player(ServerLevel server) {
        if (uuid == null) {
            return null;
        }
        return server.getPlayerByUUID(uuid) instanceof ServerPlayer bound ? bound : null;
    }

    /** 箱子绑定的那个人，或在箱子持有未认领的脑时任何人都可以。 */
    boolean mayTakeBrain(Player player) {
        return uuid == null || uuid.equals(player.getUUID());
    }

    void save(CompoundTag tag) {
        if (uuid != null) {
            tag.putUUID(TAG_OWNER, uuid);
            tag.putString(TAG_OWNER_NAME, name);
        }
    }

    void load(CompoundTag tag) {
        this.uuid = tag.hasUUID(TAG_OWNER) ? tag.getUUID(TAG_OWNER) : null;
        this.name = tag.getString(TAG_OWNER_NAME);
    }
}
