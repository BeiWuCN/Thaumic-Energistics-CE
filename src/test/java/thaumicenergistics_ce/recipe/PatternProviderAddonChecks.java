package thaumicenergistics_ce.recipe;

import appeng.api.config.Actionable;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.helpers.patternprovider.PatternProviderTarget;
import appeng.menu.implementations.PatternProviderMenu;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aspect.TTAspects;
import com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities;
import com.leclowndu93150.thaumaturge.registry.TTBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import thaumicenergistics_ce.integration.ae2.patternprovider.EssentiaPatternProvider;
import thaumicenergistics_ce.registry.TECEItems;

final class PatternProviderAddonChecks {
    static void run(MinecraftServer server) {
        if (!net.neoforged.fml.ModList.get().isLoaded("extendedae")) {
            System.out.println("[pattern-provider-addons] PASS: optional addons absent, no hard dependency");
            return;
        }
        var level = server.overworld();
        var player = FakePlayerFactory.getMinecraft(level);
        var pos = new BlockPos(48, 240, 48);
        var aspect = server.registryAccess().lookupOrThrow(IAspect.REGISTRY_KEY).getOrThrow(TTAspects.IGNIS);
        var key = AEssentiaKey.of(aspect);
        int tested = 0;
        try {
            for (var entry : BuiltInRegistries.BLOCK.entrySet()) {
                var id = entry.getKey().identifier();
                if (!(id.getNamespace().equals("extendedae") || id.getNamespace().equals("extendedae_plus"))
                        || !id.getPath().contains("pattern_provider")) continue;
                require(level.getBlockState(pos).isAir(), "Addon fixture occupied");
                level.setBlock(pos, entry.getValue().defaultBlockState(), 3);
                try {
                    if (!(level.getBlockEntity(pos) instanceof PatternProviderLogicHost host)) continue;
                    var logic = host.getLogic();
                    var extension = (EssentiaPatternProvider) logic;
                    var cards = extension.essentiaCardInventory();
                    PatternProviderMenu menu;
                    if (host.getClass().getName().contains("TileExPatternProvider")) {
                        var type = Class.forName("com.glodblock.github.extendedae.container.ContainerExPatternProvider");
                        var ctor = type.getDeclaredConstructor(int.class, Inventory.class, PatternProviderLogicHost.class);
                        ctor.setAccessible(true);
                        menu = (PatternProviderMenu) ctor.newInstance(0, player.getInventory(), host);
                    } else {
                        menu = new PatternProviderMenu(PatternProviderMenu.TYPE, 0, player.getInventory(), host);
                    }
                    var slot = menu.slots.stream().filter(menu::isPlayerSideSlot).findFirst().orElseThrow();
                    require(menu.getSlots(appeng.menu.SlotSemantics.UPGRADE).stream()
                            .filter(s -> s.mayPlace(new ItemStack(TECEItems.ESSENTIA_ACCESS_CARD.get()))).count() == 1,
                            "Addon exposed duplicate/manual wrong card slots: " + id);
                    var previous = slot.getItem().copy();
                    try {
                        slot.set(new ItemStack(TECEItems.ESSENTIA_ACCESS_CARD.get(), 2));
                        menu.quickMoveStack(player, slot.index);
                        require(cards.getStackInSlot(0).getCount() == 1 && slot.getItem().getCount() == 1,
                                "Addon Shift-click did not use the dedicated card slot: " + id);
                        menu.quickMoveStack(player, slot.index);
                        require(slot.getItem().getCount() == 1, "Addon full slot consumed remaining card: " + id);
                    } finally { slot.set(previous); }
                    var receive = level.getCapability(EssentiaCapabilities.STORAGE, pos, Direction.UP);
                    require(receive != null, "Addon return capability missing: " + id);
                    try (var tx = Transaction.openRoot()) {
                        require(receive.insert(aspect, 5, tx) == 5, "Addon receive failed: " + id);
                    }
                    require(logic.getReturnInv().isEmpty(), "Addon return preview consumed source: " + id);
                    level.setBlock(pos.below(), TTBlocks.JAR_NORMAL.get().defaultBlockState(), 3);
                    var method = PatternProviderLogic.class.getDeclaredMethod("findAdapter", Direction.class);
                    method.setAccessible(true);
                    var target = (PatternProviderTarget) method.invoke(logic, Direction.DOWN);
                    PatternProviderConfigChecks.run(host, target, aspect, menu);
                    require(target.insert(key, 3, Actionable.MODULATE) == 3, "Addon could not send essentia: " + id);
                    cards.extractItem(0, 1, false);
                    require(target.insert(key, 3, Actionable.MODULATE) == 0, "Addon retained send permission: " + id);
                    tested++;
                    System.out.println("[pattern-provider-addons] PASS: " + id + " actual menu, dedicated Shift-click, receive rollback, send, live card removal");
                } finally {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    level.setBlock(pos.below(), Blocks.AIR.defaultBlockState(), 3);
                }
            }
            require(tested > 0, "ExtendedAE installed but no provider was tested");
            // The extended provider part is not a subclass of AE2's PatternProviderPart.
            level.setBlock(pos, appeng.core.definitions.AEBlocks.CABLE_BUS.block().defaultBlockState(), 3);
            var partHost = (appeng.api.parts.IPartHost) level.getBlockEntity(pos);
            for (var entry : BuiltInRegistries.ITEM.entrySet()) {
                var id = entry.getKey().identifier();
                if (id.getNamespace().equals("extendedae") && id.getPath().contains("pattern_provider")
                        && entry.getValue() instanceof appeng.api.parts.IPartItem<?> item) {
                    var part = partHost.addPart(item, Direction.UP, player);
                    require(part != null && level.getCapability(EssentiaCapabilities.STORAGE, pos, Direction.UP) != null,
                            "ExtendedAE panel provider return capability missing");
                    System.out.println("[pattern-provider-addons] PASS: " + id + " panel return capability");
                    break;
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        } finally {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(pos.below(), Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
