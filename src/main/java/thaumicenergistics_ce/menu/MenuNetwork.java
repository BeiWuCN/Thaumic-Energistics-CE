package thaumicenergistics_ce.menu;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics_ce.network.EncoderActionPayload;
import thaumicenergistics_ce.network.EncoderSourcePayload;
import thaumicenergistics_ce.network.InscriberGridFillPayload;
import thaumicenergistics_ce.network.InscriberGridPayload;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * 菜单发给服务端的四个请求，集中在一处构建。
 * 菜单是容器的客户端半边，由它来问；这里是菜单唯一指名载荷的位置。
 * 请求在这里构建，不在 {@code net} 里：后者只是两侧的约定，不该知道哪个菜单决定了什么。
 */
public final class MenuNetwork {

    private MenuNetwork() {}

    /** 选中传入值对应的要素，值为负就清除选择。 */
    public static final int ACTION_SELECT = EncoderActionPayload.ACTION_SELECT;

    /** 用当前的源物品、要素和空白样板写出一个样板。 */
    public static final int ACTION_ENCODE = EncoderActionPayload.ACTION_ENCODE;

    /** 为拖拽把一个空白样板移进空白槽位，不能凭空变出来：编码会消耗它。 */
    public static final int ACTION_INSERT_BLANK = EncoderActionPayload.ACTION_INSERT_BLANK;

    /** 铭刻机幽灵网格的一个单元；物品堆送来时已裁成一个物品。 */
    public static void sendInscriberGrid(int containerId, int cell, ItemStack stack) {
        ClientPacketDistributor.sendToServer(new InscriberGridPayload(containerId, cell, stack));
    }

    /** 一次写完整个铭刻机网格：每单元一个载荷会对着半边网格反复求解。 */
    public static void sendInscriberGridFill(int containerId, List<ItemStack> cells) {
        ClientPacketDistributor.sendToServer(new InscriberGridFillPayload(containerId, List.copyOf(cells)));
    }

    /** 蒸馏编码器按哪个要素工作。 */
    public static void sendEncoderSource(int containerId, ItemStack stack) {
        ClientPacketDistributor.sendToServer(new EncoderSourcePayload(containerId, stack.copy()));
    }

    /** 编码器按钮：select / encode / insert，编成一个 int 加值。 */
    public static void sendEncoderAction(int containerId, int action, int value) {
        ClientPacketDistributor.sendToServer(new EncoderActionPayload(containerId, action, value));
    }
}
