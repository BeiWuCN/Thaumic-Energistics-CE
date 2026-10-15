package thaumicenergistics_ce;

import appeng.api.config.Actionable;
import appeng.api.features.Locatables;
import appeng.api.ids.AEComponents;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyService;
import appeng.core.definitions.AEItems;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumicenergistics_ce.init.ModItems;
import thaumicenergistics_ce.item.QuantumTerminalSupport;
import thaumicenergistics_ce.item.WirelessArcaneCraftingTerminalMenuHost;

@GameTestHolder(ThEIds.MODID)
@PrefixGameTestTemplate(false)
public final class PortQuantumRegression {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void cardFrequencyPowerAndUnloading(GameTestHelper helper) {
        if (!QuantumTerminalSupport.enabled()) { helper.succeed(); return; }
        var level = helper.getLevel();
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
        var item = ModItems.WIRELESS_ARCANE_CRAFTING_TERMINAL.get();
        var stack = new ItemStack(item);
        item.injectAEPower(stack, 10000, Actionable.MODULATE);
        var host = new WirelessArcaneCraftingTerminalMenuHost(item, player,
                appeng.menu.locator.MenuLocators.forStack(stack), (p, menu) -> {}) {
            void refresh() { updateConnectedAccessPoint(); }
        };
        var powered = new AtomicBoolean(true);
        var loaded = new AtomicBoolean(true);
        var energy = proxy(IEnergyService.class, (name) -> name.equals("isNetworkPowered") ? powered.get() : null);
        var grid = proxy(IGrid.class, name -> name.equals("getEnergyService") ? energy : null);
        var node = proxy(IGridNode.class, name -> name.equals("getGrid") ? grid : null);
        long frequency = 9285720921L;
        // Register in a different dimension: AE2's locatable lookup is server-wide.
        var bridgeLevel = level.getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        Locatables.quantumNetworkBridges().register(bridgeLevel, -frequency, () -> loaded.get() ? node : null);
        try {
            var singularity = AEItems.QUANTUM_ENTANGLED_SINGULARITY.stack();
            singularity.set(AEComponents.ENTANGLED_SINGULARITY_ID, frequency);
            host.singularityInventory().setItemDirect(0, singularity);
            host.refresh();
            require(!host.getLinkStatus().connected(), "Singularity alone connected");
            host.getUpgrades().setItemDirect(0, new ItemStack(QuantumTerminalSupport.card()));
            host.refresh();
            require(host.quantumConnected() && host.getActionableNode() == node, "Cross-dimension quantum connection required AP");
            powered.set(false);
            require(!host.getLinkStatus().connected() && host.getActionableNode() == null, "Power loss retained node");
            powered.set(true); host.refresh();
            require(host.getLinkStatus().connected(), "Powered bridge did not reconnect");
            loaded.set(false);
            require(!host.getLinkStatus().connected(), "Unloaded bridge retained connection");
            loaded.set(true); host.refresh();
            host.getUpgrades().setItemDirect(0, ItemStack.EMPTY);
            require(!host.getLinkStatus().connected(), "Removed card retained connection");
            host.getUpgrades().setItemDirect(0, new ItemStack(QuantumTerminalSupport.card()));
            singularity.set(AEComponents.ENTANGLED_SINGULARITY_ID, frequency + 1);
            host.singularityInventory().setItemDirect(0, singularity);
            host.refresh();
            require(!host.getLinkStatus().connected(), "Mismatched frequency connected");
            System.out.println("PORT PASS quantum card, matching frequency, cross-dimension, power loss, unloading and removal");
            helper.succeed();
        } finally {
            Locatables.quantumNetworkBridges().unregister(bridgeLevel, -frequency);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.util.function.Function<String, Object> values) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (instance, method, args) -> {
            Object value = values.apply(method.getName());
            if (value != null) return value;
            if (method.getReturnType() == boolean.class) return false;
            if (method.getReturnType() == double.class) return 0.0;
            if (method.getReturnType() == int.class) return 0;
            return null;
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
