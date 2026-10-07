package thaumicenergistics_ce.menu;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumicenergistics_ce.network.EncoderActionPayload;
import thaumicenergistics_ce.network.EncoderSourcePayload;
import thaumicenergistics_ce.network.InscriberGridFillPayload;
import thaumicenergistics_ce.network.InscriberGridPayload;

/**
 * 菜单发给服务端的四个请求，集中在一处构建。
 * 菜单是容器的客户端半边，所以由它来问，而这里是菜单唯一指名载荷的地方。
 * 请求在此构建而不在 {@code net} 里，
 * 后者只是两侧约定之物，不得知道某个菜单决定了什么。
 */
public final class MenuNetwork {

    private MenuNetwork() {}

    /** 选中传入值处的要素，值为负时清除选择。 */
    public static final int ACTION_SELECT = EncoderActionPayload.ACTION_SELECT;

    /** 用当前的源物品、要素与空白样板写入一个样板。 */
    public static final int ACTION_ENCODE = EncoderActionPayload.ACTION_ENCODE;

    /** 为拖拽把一个空白样板移入空白槽位，不得凭空变出一个：编码会消耗它。 */
    public static final int ACTION_INSERT_BLANK = EncoderActionPayload.ACTION_INSERT_BLANK;

    /** 铭刻机幽灵网格的一个单元；物品堆送达时已裁剪为一个物品。 */
    public static void sendInscriberGrid(int containerId, int cell, ItemStack stack) {
        PacketDistributor.sendToServer(new InscriberGridPayload(containerId, cell, stack));
    }

    /** 一次写入整个铭刻机网格：每单元一个载荷会对着半边网格重复求解。 */
    public static void sendInscriberGridFill(int containerId, List<ItemStack> cells) {
        PacketDistributor.sendToServer(new InscriberGridFillPayload(containerId, List.copyOf(cells)));
    }

    /** 蒸馏编码器应以哪个要素为工作依据。 */
    public static void sendEncoderSource(int containerId, ItemStack stack) {
        PacketDistributor.sendToServer(new EncoderSourcePayload(containerId, stack.copy()));
    }

    /** 编码器按钮：select / encode / insert，编码为一个 int 加其值。 */
    public static void sendEncoderAction(int containerId, int action, int value) {
        PacketDistributor.sendToServer(new EncoderActionPayload(containerId, action, value));
    }
}
