package thaumicenergistics_ce.menu;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics_ce.network.EncoderActionPayload;
import thaumicenergistics_ce.network.EncoderSourcePayload;
import thaumicenergistics_ce.network.InscriberGridFillPayload;
import thaumicenergistics_ce.network.InscriberGridPayload;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public final class MenuNetwork {

    private MenuNetwork() {}

    public static final int ACTION_SELECT = EncoderActionPayload.ACTION_SELECT;

    public static final int ACTION_ENCODE = EncoderActionPayload.ACTION_ENCODE;

    public static final int ACTION_INSERT_BLANK = EncoderActionPayload.ACTION_INSERT_BLANK;

    public static void sendInscriberGrid(int containerId, int cell, ItemStack stack) {
        ClientPacketDistributor.sendToServer(new InscriberGridPayload(containerId, cell, stack));
    }

    public static void sendInscriberGridFill(int containerId, List<ItemStack> cells) {
        ClientPacketDistributor.sendToServer(new InscriberGridFillPayload(containerId, List.copyOf(cells)));
    }

    public static void sendEncoderSource(int containerId, ItemStack stack) {
        ClientPacketDistributor.sendToServer(new EncoderSourcePayload(containerId, stack.copy()));
    }

    public static void sendEncoderAction(int containerId, int action, int value) {
        ClientPacketDistributor.sendToServer(new EncoderActionPayload(containerId, action, value));
    }
}
