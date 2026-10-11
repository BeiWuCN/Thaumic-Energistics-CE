package thaumicenergistics_ce.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import thaumicenergistics_ce.ThEIds;

public record EncoderActionPayload(int containerId, int action, int value) implements CustomPacketPayload {

    public static final int ACTION_SELECT = 0;

    public static final int ACTION_ENCODE = 1;

    public static final int ACTION_INSERT_BLANK = 2;

    public static final Type<EncoderActionPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(ThEIds.MODID, "encoder_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EncoderActionPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    EncoderActionPayload::containerId,
                    ByteBufCodecs.VAR_INT,
                    EncoderActionPayload::action,
                    ByteBufCodecs.VAR_INT,
                    EncoderActionPayload::value,
                    EncoderActionPayload::new);

    @Override
    public Type<EncoderActionPayload> type() {
        return TYPE;
    }

    public void handle(Player player) {
        if (!(player.containerMenu instanceof DistillationEncoderReceiver receiver)
                || receiver.containerId() != containerId) {
            return;
        }
        switch (action) {
            case ACTION_SELECT -> receiver.selectAspect(value);
            case ACTION_ENCODE -> receiver.encode();
            case ACTION_INSERT_BLANK -> receiver.insertBlankFromInventory(player);
            default -> {
            }
        }
    }
}
