package thaumicenergistics_ce.blockentity.gachabox;

import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

final class GachaOwner {

    private static final String TAG_OWNER = "Owner";
    private static final String TAG_OWNER_NAME = "OwnerName";

    private @Nullable UUID uuid;
    private String name = "";

    boolean isBound() {
        return uuid != null;
    }

    void bind(Player player) {
        this.uuid = player.getUUID();
        this.name = player.getName().getString();
    }

    void unbind() {
        this.uuid = null;
        this.name = "";
    }

    @Nullable String name() {
        return uuid == null ? null : name;
    }

    @Nullable ServerPlayer player(ServerLevel server) {
        if (uuid == null) {
            return null;
        }
        return server.getPlayerByUUID(uuid) instanceof ServerPlayer bound ? bound : null;
    }

    boolean mayTakeBrain(Player player) {
        return uuid == null || uuid.equals(player.getUUID());
    }

    void save(ValueOutput output) {
        if (uuid != null) {
            output.store(TAG_OWNER, UUIDUtil.CODEC, uuid);
            output.putString(TAG_OWNER_NAME, name);
        }
    }

    void load(ValueInput input) {
        this.uuid = input.read(TAG_OWNER, UUIDUtil.CODEC).orElse(null);
        this.name = input.getStringOr(TAG_OWNER_NAME, "");
    }
}
