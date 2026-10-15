package thaumicenergistics_ce.client.render;

import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import thaumicenergistics_ce.ThEIds;

@EventBusSubscriber(modid = ThEIds.MODID, value = Dist.CLIENT)
public final class GolemBackpackReload {
    private GolemBackpackReload() {}

    @SubscribeEvent
    public static void register(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) GolemBackpackModel::reload);
    }
}
