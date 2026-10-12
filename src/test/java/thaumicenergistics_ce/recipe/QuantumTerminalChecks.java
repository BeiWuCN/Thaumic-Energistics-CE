package thaumicenergistics_ce.recipe;

import appeng.api.config.Actionable;
import appeng.api.features.Locatables;
import appeng.api.ids.AEComponents;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionHost;
import appeng.api.storage.MEStorage;
import appeng.api.stacks.KeyCounter;
import appeng.core.definitions.AEItems;
import appeng.menu.locator.MenuLocators;
import appeng.menu.locator.ItemMenuHostLocator;
import java.lang.reflect.Proxy;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import thaumicenergistics_ce.registry.TECEItems;
import thaumicenergistics_ce.item.QuantumTerminalSupport;
import thaumicenergistics_ce.item.QuantumWirelessTerminalMenuHost;

final class QuantumTerminalChecks {
    static void run(MinecraftServer server) {
        if (!QuantumTerminalSupport.enabled()) {
            for (var item : List.of(TECEItems.WIRELESS_ESSENTIA_TERMINAL.get(), TECEItems.WIRELESS_ARCANE_CRAFTING_TERMINAL.get())) {
                require(item.getUpgrades(new ItemStack(item)).size() == 2, "Original slot count changed without WTLib");
            }
            System.out.println("[quantum-terminal] PASS: optional dependency absent, original upgrade slots retained");
            return;
        }
        quantumChecks(server);
        System.out.println("[quantum-terminal] PASS: both quantum terminals, card limits, singularity persistence/filter, bridge power loss, card/singularity removal, cross-dimension crafting");
    }

    private static void quantumChecks(MinecraftServer server) {
        var level = server.overworld();
        var player = FakePlayerFactory.getMinecraft(level);
        var original = player.getMainHandItem().copy();
        var originalMode = player.gameMode.getGameModeForPlayer();
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        long frequency = 9876543212345L;
        boolean[] powered = {true};
        var energy = proxy(IEnergyService.class, (method, args) -> switch (method) {
            case "isNetworkPowered" -> powered[0];
            case "getStoredPower" -> 0.0;
            case "getMaxStoredPower" -> 10000.0;
            default -> throw new IllegalStateException("Unexpected energy call: " + method);
        });
        var storage = proxy(MEStorage.class, (method, args) -> switch (method) {
            case "getAvailableStacks" -> {
                var available = args == null || args.length == 0 ? new KeyCounter() : (KeyCounter) args[0];
                available.add(appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.STONE), 32);
                yield args == null || args.length == 0 ? available : null;
            }
            default -> throw new IllegalStateException("Unexpected storage call: " + method);
        });
        var service = proxy(appeng.api.networking.storage.IStorageService.class, (method, args) -> switch (method) {
            case "getInventory" -> storage;
            case "addListener", "removeListener" -> null;
            default -> throw new IllegalStateException("Unexpected storage service call: " + method);
        });
        var grid = proxy(IGrid.class, (method, args) -> switch (method) {
            case "getEnergyService" -> energy;
            case "getStorageService" -> service;
            case "getService" -> args[0] == IEnergyService.class ? energy : service;
            default -> throw new IllegalStateException("Unexpected grid call: " + method);
        });
        var node = proxy(IGridNode.class, (method, args) -> switch (method) {
            case "getGrid" -> grid;
            case "isActive" -> false;
            case "isPowered" -> true;
            case "getLevel" -> level;
            default -> throw new IllegalStateException("Unexpected node call: " + method);
        });
        try {
            Locatables.quantumNetworkBridges().register(level, -frequency, (IActionHost) () -> node);
            for (var item : List.of(TECEItems.WIRELESS_ESSENTIA_TERMINAL.get(), TECEItems.WIRELESS_ARCANE_CRAFTING_TERMINAL.get())) {
                var stack = new ItemStack(item);
                player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                item.injectAEPower(stack, 1000, Actionable.MODULATE);
                var upgrades = item.getUpgrades(stack);
                require(upgrades.size() == 3, "Missing quantum upgrade slot");
                require(upgrades.insertItem(2, new ItemStack(QuantumTerminalSupport.card()), false).isEmpty(), "Quantum card refused");
                require(!upgrades.insertItem(1, new ItemStack(QuantumTerminalSupport.card()), false).isEmpty(), "Duplicate quantum card accepted");
                var locator = (ItemMenuHostLocator) MenuLocators.forHand(player, InteractionHand.MAIN_HAND);
                var host = (QuantumWirelessTerminalMenuHost<?>) item.getMenuHost(player, locator, null);
                require(host.getInventory().getAvailableStacks().isEmpty(), "Disconnected inventory was not empty");
                var singularity = AEItems.QUANTUM_ENTANGLED_SINGULARITY.stack();
                singularity.set(AEComponents.ENTANGLED_SINGULARITY_ID, frequency);
                var inv = host.singularityInventory();
                require(!inv.insertItem(0, AEItems.SINGULARITY.stack(), false).isEmpty(), "Unentangled singularity accepted");
                require(inv.insertItem(0, singularity, false).isEmpty(), "Entangled singularity refused");
                host.tick();
                require(host.getActionableNode() == node, "Quantum connection did not resolve");
                require(host.getLinkStatus().connected() == !(item instanceof thaumicenergistics_ce.item.ItemWirelessArcaneCraftingTerminal),
                        "Unbound arcane terminal bypassed required binding");
                double spent = 1000 - item.getAECurrentPower(stack);
                require(spent > 0 && Math.abs(spent / 22.5 - Math.rint(spent / 22.5)) < 0.001,
                        "Quantum idle cost differs from WTLib: " + spent);
                var type = item == TECEItems.WIRELESS_ESSENTIA_TERMINAL.get()
                        ? thaumicenergistics_ce.registry.TECEMenuTypes.WIRELESS_ESSENTIA_TERMINAL.get()
                        : thaumicenergistics_ce.registry.TECEMenuTypes.WIRELESS_ARCANE_CRAFTING_TERMINAL.get();
                var menu = item == TECEItems.WIRELESS_ESSENTIA_TERMINAL.get()
                        ? new thaumicenergistics_ce.menu.MenuEssentiaTerminal(type, 0, player.getInventory(), host)
                        : new thaumicenergistics_ce.menu.MenuArcaneCraftingTerminal(type, 0, player.getInventory(), host);
                var quantumSlot = menu.getSlots(thaumicenergistics_ce.menu.MenuEssentiaTerminalBase.QUANTUM_SINGULARITY).getFirst();
                var removed = host.singularityInventory().extractItem(0, 1, false);
                int playerSlot = menu.slots.stream().filter(slot -> slot.container == player.getInventory() && slot.mayPickup(player)
                        && slot.getContainerSlot() == 9)
                        .mapToInt(slot -> slot.index).findFirst().orElseThrow();
                var inventorySlot = menu.getSlot(playerSlot);
                var previous = inventorySlot.getItem().copy();
                try {
                    inventorySlot.set(removed.copyWithCount(2));
                    menu.quickMoveStack(player, playerSlot);
                    require(quantumSlot.getItem().getCount() == 1
                            && inventorySlot.getItem().getCount() == 1,
                            "Shift-click did not put one singularity in its dedicated slot");
                    menu.quickMoveStack(player, playerSlot);
                    require(quantumSlot.getItem().getCount() == 1 && inventorySlot.getItem().getCount() == 1,
                            "Shift-click into a full singularity slot consumed the remainder");
                } finally {
                    inventorySlot.set(previous);
                }
                require(menu.getSlots(thaumicenergistics_ce.menu.MenuEssentiaTerminalBase.QUANTUM_SINGULARITY).size() == 1,
                        "Singularity slot missing from terminal menu");
                require(menu.getSlots(appeng.menu.SlotSemantics.UPGRADE).size()
                        == 3,
                        "Upgrade/shared card slot missing from menu");
                if (menu instanceof thaumicenergistics_ce.menu.MenuEssentiaTerminal
                        && !(menu instanceof thaumicenergistics_ce.menu.MenuArcaneCraftingTerminal)) {
                    require(menu.getSlots(appeng.menu.SlotSemantics.UPGRADE).stream()
                            .filter(net.minecraft.world.inventory.Slot::isActive).count() == 1,
                            "Wireless essentia terminal exposes unused upgrade slots");
                }
                require(host.getInventory().getAvailableStacks().get(
                        appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.STONE)) == 32,
                        "Quantum storage stayed empty after inserting the singularity");
                require(!node.isActive(), "Fixture must exercise a powered bridge without an active channel");
                require(QuantumTerminalSupport.singularity(stack.copy()).get(AEComponents.ENTANGLED_SINGULARITY_ID) == frequency, "Frequency did not persist");
                powered[0] = false;
                host.tick();
                require(host.getActionableNode() == null, "Unpowered bridge remained accessible");
                powered[0] = true;
                host.tick();
                require(host.getActionableNode() == node, "Bridge did not reconnect");
                host.getUpgrades().extractItem(2, 1, false);
                host.tick();
                require(host.getActionableNode() == null, "Card removal did not disconnect");
                require(host.getInventory().getAvailableStacks().isEmpty(), "Card removal retained cached storage access");
                host.getUpgrades().insertItem(2, new ItemStack(QuantumTerminalSupport.card()), false);
                host.tick();
                require(host.getActionableNode() == node, "Reinserted card did not reconnect");
                host.singularityInventory().extractItem(0, 1, false);
                host.tick();
                require(host.getActionableNode() == null, "Singularity removal did not disconnect");
            }
            crossDimensionChecks(server, node, frequency);
        } finally {
            Locatables.quantumNetworkBridges().unregister(level, -frequency);
            player.setItemInHand(InteractionHand.MAIN_HAND, original);
            player.setGameMode(originalMode);
        }
    }

    private static void crossDimensionChecks(MinecraftServer server, IGridNode quantumNode, long frequency) {
        var sourceLevel = server.overworld();
        var remoteLevel = server.getLevel(net.minecraft.world.level.Level.NETHER);
        require(remoteLevel != null, "Missing Nether fixture");
        var player = FakePlayerFactory.getMinecraft(remoteLevel);
        var original = player.getMainHandItem().copy();
        var pos = new BlockPos(16, 248, 16);
        require(sourceLevel.getBlockState(pos).isAir(), "Cross-dimension fixture occupied");
        try {
            sourceLevel.setBlock(pos, appeng.core.definitions.AEBlocks.CABLE_BUS.block().defaultBlockState(), 3);
            var partHost = (appeng.api.parts.IPartHost) sourceLevel.getBlockEntity(pos);
            var part = partHost.addPart(TECEItems.ARCANE_CRAFTING_TERMINAL.get(), net.minecraft.core.Direction.NORTH,
                    FakePlayerFactory.getMinecraft(sourceLevel));
            var item = TECEItems.WIRELESS_ARCANE_CRAFTING_TERMINAL.get();
            var stack = new ItemStack(item);
            item.pairWith(stack, sourceLevel, pos, net.minecraft.core.Direction.NORTH);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            item.injectAEPower(stack, 1000, Actionable.MODULATE);
            item.getUpgrades(stack).insertItem(2, new ItemStack(QuantumTerminalSupport.card()), false);
            var locator = (ItemMenuHostLocator) MenuLocators.forHand(player, InteractionHand.MAIN_HAND);
            var host = (thaumicenergistics_ce.item.WirelessArcaneCraftingTerminalMenuHost) item.getMenuHost(player, locator, null);
            require(host.arcaneTerminal() == null, "Cross-dimension crafting allowed without quantum connection");
            var singularity = AEItems.QUANTUM_ENTANGLED_SINGULARITY.stack();
            singularity.set(AEComponents.ENTANGLED_SINGULARITY_ID, frequency);
            host.singularityInventory().insertItem(0, singularity, false);
            host.tick();
            require(host.getActionableNode() == quantumNode
                    && thaumicenergistics_ce.item.ItemWirelessArcaneCraftingTerminal.pairedTerminal(remoteLevel, stack, true) == part,
                    "Quantum connection could not resolve the paired terminal across dimensions");
            require(host.arcaneTerminal() == null && !host.getLinkStatus().connected(),
                    "Arcane terminal accessed a binding on another ME network");
            sourceLevel.removeBlock(pos, false);
            require(host.arcaneTerminal() == null, "Removed paired terminal remained available");
        } finally {
            sourceLevel.removeBlock(pos, false);
            player.setItemInHand(InteractionHand.MAIN_HAND, original);
        }
    }

    private interface Call { Object invoke(String method, Object[] args); }
    private static <T> T proxy(Class<T> type, Call call) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (object, method, args) -> call.invoke(method.getName(), args)));
    }
    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
