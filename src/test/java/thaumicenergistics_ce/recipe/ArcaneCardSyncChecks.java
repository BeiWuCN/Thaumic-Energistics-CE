package thaumicenergistics_ce.recipe;

import appeng.menu.locator.ItemMenuHostLocator;
import appeng.menu.locator.MenuLocators;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import thaumicenergistics_ce.registry.ModItems;
import thaumicenergistics_ce.registry.ModMenuTypes;
import thaumicenergistics_ce.item.ItemVisConnectionCard;
import thaumicenergistics_ce.item.ItemWirelessArcaneCraftingTerminal;
import thaumicenergistics_ce.item.WirelessArcaneCraftingTerminalMenuHost;
import thaumicenergistics_ce.menu.MenuArcaneCraftingTerminal;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;

final class ArcaneCardSyncChecks {
    static void run(MinecraftServer server) {
        var player = FakePlayerFactory.getMinecraft(server.overworld());
        ItemStack previous = player.getMainHandItem().copy();
        var item = ModItems.WIRELESS_ARCANE_CRAFTING_TERMINAL.get();
        var handheld = new ItemStack(item);
        var placed = new PartArcaneCraftingTerminal(ModItems.ARCANE_CRAFTING_TERMINAL.get());
        PartArcaneCraftingTerminal[] liveLink = {placed};
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, handheld);
            item.pairWith(handheld, player.level(), BlockPos.ZERO, Direction.NORTH);
            var locator = (ItemMenuHostLocator) MenuLocators.forHand(player, InteractionHand.MAIN_HAND);
            var host = new WirelessArcaneCraftingTerminalMenuHost(item, player, locator, (p, sub) -> {}) {
                @Override
                public PartArcaneCraftingTerminal arcaneTerminal() { return liveLink[0]; }
            };
            var card = new ItemStack(ModItems.VIS_CONNECTION_CARD.get());
            ItemVisConnectionCard.bind(card, player.level().dimension().identifier().toString(),
                    new BlockPos(100, 64, 100), Direction.SOUTH);
            require(placed.getUpgrades().insertItem(0, card, false).isEmpty(), "Placed card insertion failed");
            require(placed.getUpgrades().insertItem(1, new ItemStack(ModItems.ESSENTIA_ACCESS_CARD.get()), false).isEmpty(),
                    "Placed essentia card insertion failed");
            for (int i = 0; i < 8; i++) {
                var menu = new MenuArcaneCraftingTerminal(ModMenuTypes.WIRELESS_ARCANE_CRAFTING_TERMINAL.get(),
                        i, player.getInventory(), host);
                require(menu.inheritedVisConnection && menu.hasVisConnectionCard(), "Bound VIS card not inherited");
                require(ItemStack.matches(menu.visCard(), card), "Card binding data was lost");
                require(!host.getUpgrades().isInstalled(ModItems.ESSENTIA_ACCESS_CARD.get()),
                        "Another upgrade was inherited");
                require(!host.getUpgrades().isInstalled(ModItems.VIS_CONNECTION_CARD.get()),
                        "Card was copied into handheld upgrades");
                require(menu.slots.stream().noneMatch(slot -> slot.getItem().is(ModItems.VIS_CONNECTION_CARD.get())),
                        "Inherited card exposed as an extractable inventory slot");
                menu.visCard().shrink(1);
                require(placed.getUpgrades().getStackInSlot(0).getCount() == 1,
                        "Mutating the card view swallowed the physical card");
                if (i == 0) {
                    var removed = placed.getUpgrades().extractItem(0, 1, false);
                    require(!menu.hasVisConnectionCard(), "Removed card remained cached");
                    require(removed.getCount() == 1, "Card extraction changed its count");
                    placed.getUpgrades().insertItem(0, removed, false);
                    liveLink[0] = null;
                    require(!menu.hasVisConnectionCard(), "Disconnected/removed terminal retained inherited card");
                    liveLink[0] = placed;
                    require(menu.hasVisConnectionCard(), "Reconnection did not restore inherited effect");
                }
            }
            require(ItemWirelessArcaneCraftingTerminal.unbind(handheld), "Unbind failed");
            liveLink[0] = null;
            var unbound = new MenuArcaneCraftingTerminal(ModMenuTypes.WIRELESS_ARCANE_CRAFTING_TERMINAL.get(),
                    10, player.getInventory(), host);
            require(!unbound.hasVisConnectionCard(), "Unbound handheld retained the inherited card");
            require(placed.getUpgrades().getStackInSlot(0).getCount() == 1,
                    "Unbind consumed or duplicated the placed card");
            var rebound = new PartArcaneCraftingTerminal(ModItems.ARCANE_CRAFTING_TERMINAL.get());
            var otherCard = new ItemStack(ModItems.VIS_CONNECTION_CARD.get());
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
            require(drops.stream().filter(drop -> drop.is(ModItems.VIS_CONNECTION_CARD.get()))
                    .mapToInt(ItemStack::getCount).sum() == 1, "Dismantling duplicated or lost the card drop");
            rebound.clearContent();
            liveLink[0] = null;
            require(!unbound.hasVisConnectionCard(), "Dismantled terminal retained inherited card");
            var local = new ItemStack(ModItems.VIS_CONNECTION_CARD.get());
            require(host.getUpgrades().insertItem(0, local, false).isEmpty(), "Existing local card could not be retained");
            liveLink[0] = placed;
            require(ItemStack.matches(unbound.visCard(), card), "Bound card did not take precedence");
            liveLink[0] = null;
            require(ItemStack.matches(unbound.visCard(), local), "Disconnect lost the local card");
            require(host.getUpgrades().extractItem(0, 1, false).getCount() == 1,
                    "Local card was swallowed or duplicated");
            require(placed.getUpgrades().extractItem(0, 1, false).getCount() == 1,
                    "Placed card was swallowed or duplicated");
            System.out.println("[arcane-card-sync] PASS: VIS-only inheritance, binding data, repeated opens, no extractable copy, live removal/disconnect/reconnect/unbind/rebind, dismantle drops, independent local cards");
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, previous);
        }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
