package thaumicenergistics_ce.recipe;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import thaumicenergistics_ce.ThEIds;

@EventBusSubscriber(modid = ThEIds.MODID, value = Dist.CLIENT)
public final class ClientVisibilityMixinChecks {
    private static boolean verified;
    private ClientVisibilityMixinChecks() {}

    @SubscribeEvent
    public static void check(FMLClientSetupEvent event) {
        if (!"true".equalsIgnoreCase(System.getenv("THAUMICENERGISTICS_CLIENT_VISIBILITY_CHECK"))) {
            return;
        }
        event.enqueueWork(() -> {
            try {
                Class.forName("appeng.client.gui.me.common.MEStorageScreen")
                        .getDeclaredField("thaumicenergistics$lastVisibility");
                Class.forName("appeng.client.gui.implementations.IOBusScreen");
                Class.forName("appeng.client.gui.implementations.StorageBusScreen");
                Class.forName("thaumicenergistics_ce.client.jei.EssentiaBusGhostIngredientHandler");
                if (appeng.client.Hotkeys.getHotkeyMapping(
                        thaumicenergistics_ce.item.WirelessTerminalShortcuts.ESSENTIA) == null
                        || appeng.client.Hotkeys.getHotkeyMapping(
                                thaumicenergistics_ce.item.WirelessTerminalShortcuts.ARCANE) == null) {
                    throw new IllegalStateException("Wireless terminal key mappings were not registered");
                }
                verified = true;
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    @SubscribeEvent
    public static void finish(ClientTickEvent.Post event) {
        var client = Minecraft.getInstance();
        if (verified && client.screen != null && client.getOverlay() == null) {
            verified = false;
            appeng.client.gui.style.StyleManager.loadStyleDoc("/screens/wireless_essentia_terminal.json");
            appeng.client.gui.style.StyleManager.loadStyleDoc("/screens/wireless_arcane_crafting_terminal.json");
            System.out.println("[client-visibility] PASS: visibility mixin applied and client reached its first screen");
            client.stop();
        }
    }
}
