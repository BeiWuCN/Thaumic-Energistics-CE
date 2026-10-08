package thaumicenergistics_ce.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import thaumicenergistics_ce.ThEIds;

/**
 * 蒸馏编码器屏幕请服务端做的事：指令不是状态，两侧都从槽位同步已经带着的源物品推导要素列表。
 * @param containerId 作用的菜单；发给已关屏幕的数据包被忽略
 * @param action 做什么；见各常量
 * @param value {@link #ACTION_SELECT} 时选中的要素索引，其余不用
 */
public record EncoderActionPayload(int containerId, int action, int value) implements CustomPacketPayload {

    /** 选中 {@code value} 处的要素，值为负就清除选择。 */
    public static final int ACTION_SELECT = 0;

    /** 用当前的源物品、要素和空白样板写一个样板。 */
    public static final int ACTION_ENCODE = 1;

    /**
     * 为 JEI 的拖拽把一个空白样板移进空白井。拖拽若凭空变出一个就是造样板，
     * 因下一次编码会消耗落在那里的东西。
     */
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

    /**
     * 把这条指令应用到已打开的菜单。每个操作都在服务端重查自己的前置条件：
     * 屏幕只是建议，方块实体在花掉东西前再校验一次。
     */
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
                // 这个版本不认识的操作：忽略，别猜。
            }
        }
    }
}
