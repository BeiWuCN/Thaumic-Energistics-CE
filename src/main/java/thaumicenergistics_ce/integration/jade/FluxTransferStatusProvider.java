package thaumicenergistics_ce.integration.jade;

import appeng.api.integrations.igtooltip.PartTooltips;
import appeng.api.integrations.igtooltip.providers.ServerDataProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.entity.player.Player;
import thaumicenergistics_ce.part.FluxWait;
import thaumicenergistics_ce.part.PartFluxTransferInterface;

// 走 AE2 的部件注册表，不走 Jade 的：Jade 只认识方块实体。
public final class FluxTransferStatusProvider
        implements ServerDataProvider<PartFluxTransferInterface> {

    public static final FluxTransferStatusProvider INSTANCE = new FluxTransferStatusProvider();

    public static final String TAG_WAIT = "WaitReason";

    public static final String TAG_WORKING = "Working";

    private FluxTransferStatusProvider() {}

    public static void register() {
        PartTooltips.addServerData(PartFluxTransferInterface.class, INSTANCE);
    }

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
