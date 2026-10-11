package thaumicenergistics_ce.recipe;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.core.network.clientbound.MEInventoryUpdatePacket;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import appeng.menu.me.common.MEStorageMenu;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import thaumicenergistics_ce.blockentity.gachabox.GachaOdds;
import thaumicenergistics_ce.config.ThEConfig;
import thaumicenergistics_ce.init.ModItems;
import thaumicenergistics_ce.init.ModMenuTypes;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import thaumicenergistics_ce.menu.MenuArcaneCraftingTerminal;
import thaumicenergistics_ce.menu.MenuEssentiaTerminal;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;
import thaumicenergistics_ce.part.PartEssentiaTerminal;

public final class TerminalVisibilityChecks {
    private TerminalVisibilityChecks() {}

    public static void run(MinecraftServer server) {
        var inventory = FakePlayerFactory.getMinecraft(server.overworld()).getInventory();
        var key = AEssentiaKey.of(Identifier.parse("thaumaturge:ignis"));
        var host = new PartEssentiaTerminal(ModItems.ESSENTIA_TERMINAL.get());
        boolean original = ThEConfig.RESTRICT_ESSENTIA_VISIBILITY.get();
        try {
            ThEConfig.RESTRICT_ESSENTIA_VISIBILITY.set(true);
            var ordinary = new MEStorageMenu(MEStorageMenu.TYPE, 0, inventory, host);
            require(!ordinary.isKeyVisible(key), "Ordinary ME terminal exposes essentia");
            require(ordinary.isKeyVisible(AEItemKey.of(Items.STONE)), "Ordinary items were hidden");
            for (var type : java.util.List.of(ModMenuTypes.ESSENTIA_TERMINAL.get(),
                    ModMenuTypes.WIRELESS_ESSENTIA_TERMINAL.get())) {
                require(new MenuEssentiaTerminal(type, 0, inventory, host).isKeyVisible(key),
                        "Dedicated essentia terminal hides essentia");
            }
            for (var type : java.util.List.of(ModMenuTypes.ARCANE_CRAFTING_TERMINAL.get(),
                    ModMenuTypes.WIRELESS_ARCANE_CRAFTING_TERMINAL.get())) {
                var part = new PartArcaneCraftingTerminal(ModItems.ARCANE_CRAFTING_TERMINAL.get());
                var arcane = new MenuArcaneCraftingTerminal(type, 0, inventory, part);
                require(!arcane.isKeyVisible(key), "Arcane terminal without card exposes essentia");
                part.getUpgrades().setItemDirect(0, new ItemStack(ModItems.ESSENTIA_ACCESS_CARD.get()));
                require(arcane.isKeyVisible(key), "Installing card did not reveal essentia");
                part.getUpgrades().setItemDirect(0, ItemStack.EMPTY);
                require(!arcane.isKeyVisible(key), "Removing card did not hide essentia");
                liveUpdates(server, inventory, type, key);
            }
            ThEConfig.RESTRICT_ESSENTIA_VISIBILITY.set(false);
            require(ordinary.isKeyVisible(key), "Visibility setting has no effect");
            double shortChance = ThEConfig.GACHA_SHORT_CHANCE.get();
            double longChance = ThEConfig.GACHA_LONG_CHANCE.get();
            try {
                ThEConfig.GACHA_SHORT_CHANCE.set(1.0);
                ThEConfig.GACHA_LONG_CHANCE.set(0.0);
                require(GachaOdds.payoutChance(30) == 1.0 && GachaOdds.payoutChance(180) == 0.0
                        && GachaOdds.payoutChance(105) == 0.5, "Configured probabilities ignored");
            } finally {
                ThEConfig.GACHA_SHORT_CHANCE.set(shortChance);
                ThEConfig.GACHA_LONG_CHANCE.set(longChance);
            }
        } finally {
            ThEConfig.RESTRICT_ESSENTIA_VISIBILITY.set(original);
        }
        System.out.println("[terminal-visibility] PASS: both arcane menus resend unchanged stock on card insertion/removal; stable ticks stay incremental; empty inventory clears; ordinary/dedicated rules and config switch");
    }

    private static void liveUpdates(MinecraftServer server, net.minecraft.world.entity.player.Inventory inventory,
            net.minecraft.world.inventory.MenuType<?> type, AEssentiaKey key) {
        var player = (ServerPlayer) inventory.player;
        var originalConnection = player.connection;
        var listener = new RecordingListener(server, player);
        player.connection = listener;
        try {
            var stock = new KeyCounter();
            stock.add(key, 32);
            stock.add(AEItemKey.of(Items.STONE), 4);
            var part = new PartArcaneCraftingTerminal(ModItems.ARCANE_CRAFTING_TERMINAL.get()) {
                @Override
                public MEStorage getInventory() {
                    return new MEStorage() {
                        @Override public void getAvailableStacks(KeyCounter out) { out.addAll(stock); }
                        @Override public Component getDescription() { return Component.literal("visibility fixture"); }
                    };
                }
            };
            var menu = new MenuArcaneCraftingTerminal(type, 17, inventory, part);
            menu.broadcastChanges();
            var initial = listener.updates.stream().flatMap(p -> p.getActualEntries().stream()).toList();
            require(initial.stream().noneMatch(e -> key.equals(e.getWhat())),
                    "Initial update exposed essentia without card");
            long stoneSerial = initial.stream().filter(e -> AEItemKey.of(Items.STONE).equals(e.getWhat()))
                    .mapToLong(e -> e.getSerial()).findFirst().orElseThrow();
            listener.updates.clear();
            part.getUpgrades().setItemDirect(0, new ItemStack(ModItems.ESSENTIA_ACCESS_CARD.get()));
            menu.broadcastChanges();
            var revealed = listener.updates.stream().filter(p -> p.fullUpdate())
                    .flatMap(p -> p.getActualEntries().stream()).toList();
            require(revealed.stream().anyMatch(e -> key.equals(e.getWhat()) && e.getStoredAmount() == 32),
                    "Installing card did not send unchanged essentia stock");
            require(revealed.stream().anyMatch(e -> AEItemKey.of(Items.STONE).equals(e.getWhat())
                    && e.getSerial() == stoneSerial), "Permission refresh changed an existing entry serial");
            listener.updates.clear();
            menu.broadcastChanges();
            require(listener.updates.isEmpty(), "Unchanged permissions resent the inventory every tick");
            part.getUpgrades().setItemDirect(0, ItemStack.EMPTY);
            menu.broadcastChanges();
            require(listener.updates.stream().anyMatch(p -> p.fullUpdate()) && listener.updates.stream()
                    .flatMap(p -> p.getActualEntries().stream()).noneMatch(e -> key.equals(e.getWhat())),
                    "Removing card did not replace the visible inventory");
            listener.updates.clear();
            stock.clear();
            part.getUpgrades().setItemDirect(0, new ItemStack(ModItems.ESSENTIA_ACCESS_CARD.get()));
            menu.broadcastChanges();
            require(listener.updates.stream().anyMatch(p -> p.fullUpdate() && p.getActualEntries().isEmpty()),
                    "Empty inventory did not send a clearing full update");
        } finally {
            player.connection = originalConnection;
        }
    }

    private static final class RecordingListener extends ServerGamePacketListenerImpl {
        final List<MEInventoryUpdatePacket> updates = new ArrayList<>();
        RecordingListener(MinecraftServer server, ServerPlayer player) {
            super(server, new Connection(PacketFlow.SERVERBOUND), player,
                    CommonListenerCookie.createInitial(player.getGameProfile(), false));
        }
        @Override
        public void send(Packet<?> packet) {
            if (packet instanceof ClientboundCustomPayloadPacket custom
                    && custom.payload() instanceof MEInventoryUpdatePacket update) {
                updates.add(update);
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
