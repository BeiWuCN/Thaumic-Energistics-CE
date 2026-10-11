package thaumicenergistics_ce.recipe;

import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPart;
import appeng.api.stacks.AEKey;
import appeng.api.storage.ILinkStatus;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.MEStorage;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.registry.TTItems;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import thaumicenergistics_ce.recipe.arcane.ArcaneTerminalHost;
import thaumicenergistics_ce.compat.thaumaturge.TcRegistry;
import thaumicenergistics_ce.registry.ModItems;
import thaumicenergistics_ce.registry.ModMenuTypes;
import thaumicenergistics_ce.menu.MenuArcaneCraftingTerminal;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;

public final class ArcaneQuickMoveChecks {
    private ArcaneQuickMoveChecks() {}

    public static void run(MinecraftServer server) {
        var player = FakePlayerFactory.getMinecraft(server.overworld());
        Inventory inventory = player.getInventory();
        List<ItemStack> saved = new ArrayList<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            saved.add(inventory.getItem(i).copy());
        }
        try {
            check(server, inventory, ModMenuTypes.ARCANE_CRAFTING_TERMINAL.get());
            check(server, inventory, ModMenuTypes.WIRELESS_ARCANE_CRAFTING_TERMINAL.get());
        } finally {
            for (int i = 0; i < saved.size(); i++) {
                inventory.setItem(i, saved.get(i));
            }
        }
        System.out.println("[arcane-quick-move] PASS: both menus, six aspects, wand, stacking, overflow and network fallback");
    }

    private static void check(MinecraftServer server, Inventory inventory, MenuType<?> type) {
        inventory.clearContent();
        var part = new PartArcaneCraftingTerminal(ModItems.ARCANE_CRAFTING_TERMINAL.get());
        var network = new TestStorage();
        ITerminalHost host = (ITerminalHost) Proxy.newProxyInstance(ArcaneQuickMoveChecks.class.getClassLoader(),
                new Class<?>[] {ITerminalHost.class, ArcaneTerminalHost.class, IEnergySource.class, IPart.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "arcaneTerminal" -> part;
                    case "getInventory" -> network;
                    case "getLinkStatus" -> ILinkStatus.ofConnected();
                    case "extractAEPower" -> args[0];
                    default -> method.invoke(part, args);
                });
        var menu = new MenuArcaneCraftingTerminal(type, 0, inventory, host);
        menu.setLinkStatus(ILinkStatus.ofConnected());
        Slot source = menu.slots.stream().filter(slot -> slot.container == inventory
                && slot.getContainerSlot() == 0).findFirst().orElseThrow();
        var aspects = server.registryAccess().lookupOrThrow(IAspect.REGISTRY_KEY);
        for (int i = 0; i < 6; i++) {
            ItemStack crystal = TcRegistry.crystalStack(aspects.getOrThrow(MenuArcaneCraftingTerminal.aspectOf(i)), 4);
            inventory.setItem(0, crystal.copy());
            menu.quickMoveStack(inventory.player, source.index);
            require(inventory.getItem(0).isEmpty(), "Crystal stayed in player inventory");
            require(ItemStack.matches(menu.crystalSlots().get(i).getItem(), crystal), "Crystal routed to wrong aspect slot");
            require(network.stored == 0, "Crystal went to network before its slot");
        }
        Slot target = menu.crystalSlots().getFirst();
        int capacity = target.getMaxStackSize(target.getItem());
        target.setByPlayer(target.getItem().copyWithCount(capacity - 1));
        inventory.setItem(0, target.getItem().copyWithCount(4));
        menu.quickMoveStack(inventory.player, source.index);
        require(target.getItem().getCount() == capacity, "Crystal stack was not topped up");
        require(network.stored == 3, "Overflow was lost or counted twice");
        ItemStack wand = new ItemStack(TTItems.WAND.get());
        wand.set(DataComponents.CUSTOM_NAME, Component.literal("quick move wand"));
        inventory.setItem(0, wand.copy());
        menu.quickMoveStack(inventory.player, source.index);
        require(ItemStack.matches(menu.wandSlot().getItem(), wand), "Wand slot or components lost");
        require(inventory.getItem(0).isEmpty() && network.stored == 3, "Wand went to network");
        ItemStack otherWand = new ItemStack(TTItems.WAND.get());
        inventory.setItem(0, otherWand);
        menu.quickMoveStack(inventory.player, source.index);
        require(ItemStack.matches(menu.wandSlot().getItem(), wand), "Occupied wand was replaced");
        require(network.stored == 4, "Occupied wand slot fallback failed");
        inventory.setItem(0, new ItemStack(Items.COBBLESTONE, 5));
        menu.quickMoveStack(inventory.player, source.index);
        require(inventory.getItem(0).isEmpty() && network.stored == 9, "Ordinary item network transfer changed");
        menu.quickMoveStack(inventory.player, menu.wandSlot().index);
        require(menu.wandSlot().getItem().isEmpty(), "Wand cannot shift back to player inventory");
        require(network.stored == 9, "Wand removal inserted into network");
    }

    private static final class TestStorage implements MEStorage {
        private long stored;

        @Override
        public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
            if (mode == Actionable.MODULATE) {
                stored += amount;
            }
            return amount;
        }

        @Override
        public Component getDescription() {
            return Component.literal("quick move network fixture");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
