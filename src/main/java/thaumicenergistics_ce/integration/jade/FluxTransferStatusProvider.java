package thaumicenergistics_ce.integration.jade;

import appeng.api.integrations.igtooltip.providers.ServerDataProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.entity.player.Player;
import thaumicenergistics_ce.part.FluxWait;
import thaumicenergistics_ce.part.PartFluxTransferInterface;

/**
 * 咒波接口的 tooltip 服务端半边。它走 AE2 自己的部件注册表，不走 Jade 的，
 * 因为 Jade 只认识方块实体；与本类配对的是
 * {@code client.jade.FluxTransferTooltip}，注册这两个的是 {@link FluxTransferTooltipProvider}。
 */
public final class FluxTransferStatusProvider
        implements ServerDataProvider<PartFluxTransferInterface> {

    public static final FluxTransferStatusProvider INSTANCE = new FluxTransferStatusProvider();

    public static final String TAG_WAIT = "WaitReason";

    public static final String TAG_WORKING = "Working";

    private FluxTransferStatusProvider() {}

    @Override
    public void provideServerData(Player player, PartFluxTransferInterface part, CompoundTag tag) {
        FluxWait wait = part.waitReason();
        if (wait != null) {
            tag.put(TAG_WAIT, encode(wait.label()));
        }
        tag.putBoolean(TAG_WORKING, part.working());
    }

    private static Tag encode(Component component) {
        return ComponentSerialization.CODEC
                .encodeStart(NbtOps.INSTANCE, component)
                .result()
                .orElse(new CompoundTag());
    }
}
