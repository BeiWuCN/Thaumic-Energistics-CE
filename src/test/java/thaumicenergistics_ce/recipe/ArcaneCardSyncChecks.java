package thaumicenergistics_ce.recipe;

import appeng.menu.locator.ItemMenuHostLocator;
import appeng.menu.locator.MenuLocators;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import thaumicenergistics_ce.registry.TECEItems;
import thaumicenergistics_ce.registry.TECEMenuTypes;
import thaumicenergistics_ce.item.ItemVisConnectionCard;
import thaumicenergistics_ce.item.ItemWirelessArcaneCraftingTerminal;
import thaumicenergistics_ce.item.WirelessArcaneCraftingTerminalMenuHost;
import thaumicenergistics_ce.menu.MenuArcaneCraftingTerminal;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;

final class ArcaneCardSyncChecks {
    static void run(MinecraftServer server) {
        var player = FakePlayerFactory.getMinecraft(server.overworld());
        ItemStack previous = player.getMainHandItem().copy();
        var item = TECEItems.WIRELESS_ARCANE_CRAFTING_TERMINAL.get();
        var handheld = new ItemStack(item);
        var placed = new PartArcaneCraftingTerminal(TECEItems.ARCANE_CRAFTING_TERMINAL.get());
        PartArcaneCraftingTerminal[] liveLink = {placed};
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, handheld);
            item.pairWith(handheld, player.level(), BlockPos.ZERO, Direction.NORTH);
            var locator = (ItemMenuHostLocator) MenuLocators.forHand(player, InteractionHand.MAIN_HAND);
            var host = new WirelessArcaneCraftingTerminalMenuHost(item, player, locator, (p, sub) -> {}) {
                @Override
                public PartArcaneCraftingTerminal arcaneTerminal() { return liveLink[0]; }
            };
            var card = new ItemStack(TECEItems.VIS_CONNECTION_CARD.get());
            ItemVisConnectionCard.bind(card, player.level().dimension().identifier().toString(),
                    new BlockPos(100, 64, 100), Direction.SOUTH);
            require(placed.getUpgrades().insertItem(0, card, false).isEmpty(), "Placed card insertion failed");
            require(placed.getUpgrades().insertItem(1, new ItemStack(TECEItems.ESSENTIA_ACCESS_CARD.get()), false).isEmpty(),
                    "Placed essentia card insertion failed");
            var wired = new MenuArcaneCraftingTerminal(TECEMenuTypes.ARCANE_CRAFTING_TERMINAL.get(),
                    90, player.getInventory(), placed);
            wired.essentiaAccessEnabled = false;
            var wiredSlots = wired.getSlots(appeng.menu.SlotSemantics.UPGRADE);
            var firstCard = placed.getUpgrades().extractItem(0, 1, false);
            var secondCard = placed.getUpgrades().extractItem(1, 1, false);
            wired.updateUpgradeSlotVisibility();
            require(!wiredSlots.get(1).isActive(), "Empty-slot visibility fixture failed");
            wired.essentiaAccessEnabled = true;
            wired.updateUpgradeSlotVisibility();
            require(wiredSlots.get(0).isActive() && wiredSlots.get(1).isActive(),
                    "Enabled physical terminal did not restore both card slots");
            wired.essentiaAccessEnabled = false;
            wired.updateUpgradeSlotVisibility();
            var wirelessVisibility = new MenuArcaneCraftingTerminal(TECEMenuTypes.WIRELESS_ARCANE_CRAFTING_TERMINAL.get(),
                    89, player.getInventory(), host);
            ArcaneJeiTransferChecks.run(wired, wirelessVisibility);
            wirelessVisibility.essentiaAccessEnabled = false;
            wirelessVisibility.updateUpgradeSlotVisibility();
            var wirelessSlots = wirelessVisibility.getSlots(appeng.menu.SlotSemantics.UPGRADE);
            require(wirelessSlots.get(0).isActive() && !wirelessSlots.get(1).isActive()
                    && wirelessSlots.get(2).isActive(), "Disabled wireless essentia slot remained visible or hid quantum slot");
            wirelessVisibility.essentiaAccessEnabled = true;
            wirelessVisibility.updateUpgradeSlotVisibility();
            require(wirelessSlots.get(1).isActive(), "Wireless essentia slot did not reappear on enable");
            var slotView = new thaumicenergistics_ce.item.SharedArcaneUpgradeInventory(host);
            require(slotView.insertItem(1, firstCard, false).isEmpty(), "Wireless second slot refused VIS card");
            wired.updateUpgradeSlotVisibility();
            require(wiredSlots.get(1).isActive() && wiredSlots.get(1).getItem().is(TECEItems.VIS_CONNECTION_CARD.get()),
                    "Hidden second block slot did not reveal the remotely inserted card");
            wirelessVisibility.essentiaAccessEnabled = false;
            wirelessVisibility.updateUpgradeSlotVisibility();
            require(wirelessSlots.get(1).isActive() && !wirelessSlots.get(0).isActive()
                    && wirelessSlots.get(2).isActive(), "Wireless visibility hid an installed card or quantum slot");
            placed.getUpgrades().extractItem(1, 1, false);
            placed.getUpgrades().insertItem(0, firstCard, false);
            placed.getUpgrades().insertItem(1, secondCard, false);
            System.out.println("[arcane-slot-visibility] PASS: enabled block/wireless slots, live toggle, quantum slot preserved, remote second-slot insertion");
            var position = player.position();
            var auraA = new BlockPos(96, 64, 96);
            var auraB = new BlockPos(112, 64, 96);
            var level = server.overworld();
            level.getChunkAt(auraA);
            level.getChunkAt(auraB);
            float beforeA = com.leclowndu93150.thaumaturge.api.aura.AuraHelper.getVis(level, auraA);
            float beforeB = com.leclowndu93150.thaumaturge.api.aura.AuraHelper.getVis(level, auraB);
            try {
                com.leclowndu93150.thaumaturge.api.aura.AuraHelper.drainVis(level, auraA, Float.MAX_VALUE, false);
                com.leclowndu93150.thaumaturge.api.aura.AuraHelper.drainVis(level, auraB, Float.MAX_VALUE, false);
                com.leclowndu93150.thaumaturge.api.aura.AuraHelper.addVis(level, auraA, 11);
                com.leclowndu93150.thaumaturge.api.aura.AuraHelper.addVis(level, auraB, 29);
                var liveMenu = new MenuArcaneCraftingTerminal(TECEMenuTypes.WIRELESS_ARCANE_CRAFTING_TERMINAL.get(),
                        91, player.getInventory(), host);
                player.setPos(auraA.getX(), auraA.getY(), auraA.getZ());
                liveMenu.refreshAvailableAura();
                require(liveMenu.availableAuraVis == 11, "Wireless VIS display ignored player chunk");
                com.leclowndu93150.thaumaturge.api.aura.AuraHelper.drainVis(level, auraA, 4, false);
                liveMenu.refreshAvailableAura();
                require(liveMenu.availableAuraVis == 7, "Wireless VIS display did not refresh after drain");
                player.setPos(auraB.getX(), auraB.getY(), auraB.getZ());
                liveMenu.refreshAvailableAura();
                require(liveMenu.availableAuraVis == 29, "Wireless VIS display did not follow a chunk change");
                System.out.println("[arcane-live-vis] PASS: player chunk, aura change, crossing chunk boundary");
            } finally {
                player.setPos(position.x, position.y, position.z);
                com.leclowndu93150.thaumaturge.api.aura.AuraHelper.drainVis(level, auraA, Float.MAX_VALUE, false);
                com.leclowndu93150.thaumaturge.api.aura.AuraHelper.drainVis(level, auraB, Float.MAX_VALUE, false);
                com.leclowndu93150.thaumaturge.api.aura.AuraHelper.addVis(level, auraA, beforeA);
                com.leclowndu93150.thaumaturge.api.aura.AuraHelper.addVis(level, auraB, beforeB);
            }
            boolean oldAccess = thaumicenergistics_ce.config.ThEConfig.ARCANE_ESSENTIA_ACCESS.get();
            try {
                thaumicenergistics_ce.config.ThEConfig.ARCANE_ESSENTIA_ACCESS.set(true);
                var shared = new thaumicenergistics_ce.item.SharedArcaneUpgradeInventory(host);
                var essentia = shared.extractItem(1, 1, false);
                require(essentia.is(TECEItems.ESSENTIA_ACCESS_CARD.get())
                        && !placed.getUpgrades().isInstalled(TECEItems.ESSENTIA_ACCESS_CARD.get()),
                        "Wireless extraction did not remove the shared physical card");
                require(shared.insertItem(1, essentia, false).isEmpty()
                        && placed.getUpgrades().isInstalled(TECEItems.ESSENTIA_ACCESS_CARD.get())
                        && !host.getUpgrades().isInstalled(TECEItems.ESSENTIA_ACCESS_CARD.get()),
                        "Wireless insertion did not use block inventory exclusively");
                for (int destination = 0; destination < 2; destination++) {
                    var vis = placed.getUpgrades().extractItem(0, 1, false);
                    var essence = placed.getUpgrades().extractItem(1, 1, false);
                    require(shared.insertItem(destination, essence, false).isEmpty()
                            && placed.getUpgrades().getStackInSlot(destination).is(TECEItems.ESSENTIA_ACCESS_CARD.get()),
                            "Essentia card did not match one of the two shared slots");
                    var returned = shared.extractItem(destination, 1, false);
                    require(returned.getCount() == 1 && shared.extractItem(destination, 1, false).isEmpty(),
                            "Shared essentia extraction duplicated the card");
                    placed.getUpgrades().insertItem(0, vis, false);
                    placed.getUpgrades().insertItem(1, returned, false);
                }
                var legacyVis = placed.getUpgrades().extractItem(0, 1, false);
                require(host.getUpgrades().insertItem(0, legacyVis, false).isEmpty(), "Legacy VIS fixture failed");
                host.migrateSharedCards();
                require(!host.getUpgrades().isInstalled(TECEItems.VIS_CONNECTION_CARD.get())
                        && shared.getStackInSlot(0).getCount() == 1, "VIS migration lost or copied the card");
                var physical = placed.getUpgrades().extractItem(1, 1, false);
                require(shared.getStackInSlot(1).isEmpty(), "Block extraction left a stale wireless card");
                require(host.getUpgrades().insertItem(1, physical, false).isEmpty(), "Legacy card fixture failed");
                host.migrateSharedCards();
                require(!host.getUpgrades().isInstalled(TECEItems.ESSENTIA_ACCESS_CARD.get())
                        && shared.getStackInSlot(1).getCount() == 1, "Migration lost or duplicated a card");
                liveLink[0] = null;
                require(shared.getStackInSlot(1).isEmpty(), "Disconnected wireless exposed the old block card");
                liveLink[0] = placed;
                require(shared.getStackInSlot(1).getCount() == 1, "Reconnect lost the shared card");
                var spare = new ItemStack(TECEItems.ESSENTIA_ACCESS_CARD.get());
                require(host.getUpgrades().insertItem(1, spare, false).isEmpty(), "Spare card fixture failed");
                host.migrateSharedCards();
                require(host.getUpgrades().getStackInSlot(1).getCount() == 1
                        && placed.getUpgrades().getStackInSlot(1).getCount() == 1, "Full block swallowed the legacy spare card");
                var savedInventory = new java.util.ArrayList<ItemStack>();
                for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                    savedInventory.add(player.getInventory().getItem(slot).copy());
                    if (slot != player.getInventory().getSelectedSlot()) {
                        player.getInventory().setItem(slot, new ItemStack(net.minecraft.world.item.Items.STONE, 64));
                    }
                }
                try {
                    host.returnConflictingCards();
                    require(host.getUpgrades().getStackInSlot(1).getCount() == 1,
                            "Full player inventory lost the conflicting card");
                    require(shared.extractItem(0, 1, false).isEmpty(),
                            "Unresolved legacy cards exposed the shared block inventory");
                } finally {
                    for (int slot = 0; slot < savedInventory.size(); slot++) {
                        player.getInventory().setItem(slot, savedInventory.get(slot));
                    }
                }
                var spareSlot = player.getInventory().getFreeSlot();
                require(spareSlot >= 0, "No room for conflict return fixture");
                var previousSpare = player.getInventory().getItem(spareSlot).copy();
                host.returnConflictingCards();
                require(!host.getUpgrades().isInstalled(TECEItems.ESSENTIA_ACCESS_CARD.get())
                        && player.getInventory().getItem(spareSlot).is(TECEItems.ESSENTIA_ACCESS_CARD.get()),
                        "Conflicting legacy card was not returned to player");
                player.getInventory().setItem(spareSlot, previousSpare);
                System.out.println("[arcane-essentia-share] PASS: bidirectional single inventory, migration, full-slot preservation, disconnect/reconnect");
            } finally { thaumicenergistics_ce.config.ThEConfig.ARCANE_ESSENTIA_ACCESS.set(oldAccess); }
            for (int i = 0; i < 8; i++) {
                var menu = new MenuArcaneCraftingTerminal(TECEMenuTypes.WIRELESS_ARCANE_CRAFTING_TERMINAL.get(),
                        i, player.getInventory(), host);
                require(menu.inheritedVisConnection && menu.hasVisConnectionCard(), "Bound VIS card not inherited");
                require(ItemStack.matches(menu.visCard(), card), "Card binding data was lost");
                var displayed = menu.getSlots(appeng.menu.SlotSemantics.UPGRADE);
                require(displayed.stream().filter(slot -> slot.getItem().is(TECEItems.VIS_CONNECTION_CARD.get())).count() == 1,
                        "Shared VIS card was missing or displayed twice");
                for (int destination = 0; destination < 2; destination++) {
                    var physical = placed.getUpgrades().extractItem(0, 1, false);
                    var essence = placed.getUpgrades().extractItem(1, 1, false);
                    var view = new thaumicenergistics_ce.item.SharedArcaneUpgradeInventory(host);
                    require(view.insertItem(destination, physical, false).isEmpty(), "VIS card rejected in one of two slots");
                    require(ItemStack.matches(placed.getUpgrades().getStackInSlot(destination), card),
                            "Card in wireless slot did not reach the physical terminal");
                    var restored = view.extractItem(destination, 1, false);
                    require(restored.getCount() == 1 && view.extractItem(destination, 1, false).isEmpty(),
                            "Shared extraction duplicated a VIS card");
                    placed.getUpgrades().insertItem(0, restored, false);
                    placed.getUpgrades().insertItem(1, essence, false);
                }
                for (int slotIndex = 0; slotIndex < 2; slotIndex++) {
                    var actualSlot = displayed.get(slotIndex);
                    var physical = placed.getUpgrades().extractItem(0, 1, false);
                    var essence = placed.getUpgrades().extractItem(1, 1, false);
                    var carried = menu.getCarried().copy();
                    try {
                        require(actualSlot.mayPlace(physical), "An empty wireless upgrade slot rejected the VIS card");
                        menu.setCarried(physical);
                        menu.clicked(actualSlot.index, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, player);
                        require(menu.getCarried().isEmpty()
                                && ItemStack.matches(placed.getUpgrades().getStackInSlot(slotIndex), card),
                                "Wireless click did not insert into the physical terminal");
                        menu.clicked(actualSlot.index, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, player);
                        require(menu.getCarried().is(TECEItems.VIS_CONNECTION_CARD.get())
                                && !placed.getUpgrades().isInstalled(TECEItems.VIS_CONNECTION_CARD.get()),
                                "Wireless click did not remove the physical card");
                        placed.getUpgrades().insertItem(0, menu.getCarried(), false);
                        menu.setCarried(ItemStack.EMPTY);
                        placed.getUpgrades().insertItem(1, essence, false);
                    } finally { menu.setCarried(carried); }
                }
                require(!host.getUpgrades().isInstalled(TECEItems.ESSENTIA_ACCESS_CARD.get()),
                        "Another upgrade was inherited");
                require(!host.getUpgrades().isInstalled(TECEItems.VIS_CONNECTION_CARD.get()),
                        "Card was copied into handheld upgrades");
                require(placed.getUpgrades().getStackInSlot(0).getCount() == 1,
                        "Shared view created or lost a physical card");
                menu.visCard().shrink(1);
                require(placed.getUpgrades().getStackInSlot(0).getCount() == 1,
                        "Mutating the card view swallowed the physical card");
                if (i == 0) {
                    var removed = placed.getUpgrades().extractItem(0, 1, false);
                    require(!menu.hasVisConnectionCard(), "Removed card remained cached");
                    require(removed.getCount() == 1, "Card extraction changed its count");
                    placed.getUpgrades().insertItem(0, removed, false);
                    liveLink[0] = null;
                    require(!menu.connectionUnchanged(), "Disconnected menu still accepted the old binding");
                    var staleView = ((appeng.menu.slot.AppEngSlot) displayed.getFirst()).getInventory();
                    require(staleView.extractItem(0, 1, false).isEmpty(), "Stale slot extracted from disconnected inventory");
                    require(!staleView.insertItem(0, new ItemStack(TECEItems.VIS_CONNECTION_CARD.get()), false).isEmpty(),
                            "Stale slot accepted a card after disconnect");
                    require(!menu.hasVisConnectionCard(), "Disconnected/removed terminal retained inherited card");
                    liveLink[0] = placed;
                    require(menu.hasVisConnectionCard() && menu.connectionUnchanged(), "Reconnection did not restore original binding");
                }
            }
            require(ItemWirelessArcaneCraftingTerminal.unbind(handheld), "Unbind failed");
            liveLink[0] = null;
            var unbound = new MenuArcaneCraftingTerminal(TECEMenuTypes.WIRELESS_ARCANE_CRAFTING_TERMINAL.get(),
                    10, player.getInventory(), host);
            require(!unbound.hasVisConnectionCard(), "Unbound handheld retained the inherited card");
            require(placed.getUpgrades().getStackInSlot(0).getCount() == 1,
                    "Unbind consumed or duplicated the placed card");
            var rebound = new PartArcaneCraftingTerminal(TECEItems.ARCANE_CRAFTING_TERMINAL.get());
            var otherCard = new ItemStack(TECEItems.VIS_CONNECTION_CARD.get());
            ItemVisConnectionCard.bind(otherCard, player.level().dimension().identifier().toString(),
                    new BlockPos(200, 64, 200), Direction.WEST);
            rebound.getUpgrades().insertItem(0, otherCard, false);
            item.pairWith(handheld, player.level(), new BlockPos(16, 64, 16), Direction.NORTH);
            liveLink[0] = rebound;
            require(ItemStack.matches(unbound.visCard(), otherCard), "Rebinding retained the previous card");
            require(placed.getUpgrades().getStackInSlot(0).getCount() == 1
                    && rebound.getUpgrades().getStackInSlot(0).getCount() == 1,
                    "Rebinding moved, consumed or copied a card");
            var drops = new java.util.ArrayList<ItemStack>();
            rebound.addAdditionalDrops(drops, false);
            require(drops.stream().filter(drop -> drop.is(TECEItems.VIS_CONNECTION_CARD.get()))
                    .mapToInt(ItemStack::getCount).sum() == 1, "Dismantling duplicated or lost the card drop");
            rebound.clearContent();
            liveLink[0] = null;
            require(!unbound.hasVisConnectionCard(), "Dismantled terminal retained inherited card");
            var local = new ItemStack(TECEItems.VIS_CONNECTION_CARD.get());
            require(host.getUpgrades().insertItem(0, local, false).isEmpty(), "Existing local card could not be retained");
            liveLink[0] = placed;
            require(ItemStack.matches(unbound.visCard(), card), "Bound card did not take precedence");
            liveLink[0] = null;
            require(ItemStack.matches(unbound.visCard(), local), "Disconnect lost the local card");
            require(host.getUpgrades().extractItem(0, 1, false).getCount() == 1,
                    "Local card was swallowed or duplicated");
            require(placed.getUpgrades().extractItem(0, 1, false).getCount() == 1,
                    "Placed card was swallowed or duplicated");
            System.out.println("[arcane-card-sync] PASS: two-slot VIS/essentia sharing, binding data, actual click removal/insertion, repeated opens, no duplicated copy, live removal/disconnect/reconnect/unbind/rebind, dismantle drops, independent local cards");
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, previous);
        }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
