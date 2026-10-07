package thaumicenergistics_ce.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import thaumicenergistics_ce.ThEIds;

/**
 * 蒸馏编码器屏幕请求服务端做的事：指令而非状态，因为两侧都
 * 从槽位同步已经携带的源物品推导出要素列表。
 *
 * @param containerId 它作用的菜单；发给已关闭屏幕的数据包被忽略
 * @param action 要做什么；见各常量
 * @param value {@link #ACTION_SELECT} 时选中的要素索引，其余情况不用
 */
public record EncoderActionPayload(int containerId, int action, int value) implements CustomPacketPayload {

    /** 选中 {@code value} 处的要素，值为负时清除选择。 */
    public static final int ACTION_SELECT = 0;

    /** 用当前的源物品、要素与空白样板写入一个样板。 */
    public static final int ACTION_ENCODE = 1;

    /**
     * 为 JEI 的拖拽把一个空白样板移入空白槽位。拖拽若凭空变出一个就等于
     * 凭空造样板，因为下一次编码会消耗落在那里的东西。
     */
    public static final int ACTION_INSERT_BLANK = 2;

    public static final Type<EncoderActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "encoder_action"));

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
     * 把这条指令应用到已打开的菜单上。每个操作都在服务端重新检查自己的前置条件：
     * 屏幕只是建议，方块实体在花费任何东西之前会再校验一次。
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
                // 这个版本不认识的操作：忽略它，而不是去猜。
            }
        }
    }
}
