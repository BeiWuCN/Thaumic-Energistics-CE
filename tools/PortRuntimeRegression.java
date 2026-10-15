package thaumicenergistics_ce;

import appeng.api.config.Actionable;
import appeng.api.parts.IPartItem;
import appeng.core.definitions.AEParts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumicenergistics_ce.init.ModItems;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import thaumicenergistics_ce.integration.ae2.EssentiaBusBuffer;
import thaumicenergistics_ce.integration.ae2.PatternProviderAddonCompat;

@GameTestHolder(ThEIds.MODID)
@PrefixGameTestTemplate(false)
public final class PortRuntimeRegression {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void wirelessInventoryIsolation(GameTestHelper helper) {
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(helper.getLevel());
        var item = ModItems.WIRELESS_ARCANE_CRAFTING_TERMINAL.get();
        var first = new ItemStack(item);
        var second = new ItemStack(item);
        var firstHost = new thaumicenergistics_ce.item.WirelessArcaneCraftingTerminalMenuHost(item, player,
                appeng.menu.locator.MenuLocators.forStack(first), (p, menu) -> {});
        var secondHost = new thaumicenergistics_ce.item.WirelessArcaneCraftingTerminalMenuHost(item, player,
                appeng.menu.locator.MenuLocators.forStack(second), (p, menu) -> {});
        firstHost.craftingGrid().setItemDirect(0, new ItemStack(net.minecraft.world.item.Items.DIAMOND, 3));
        firstHost.crystalInventory().setItemDirect(0, new ItemStack(net.minecraft.world.item.Items.EMERALD, 2));
        firstHost.wandInventory().setItemDirect(0, new ItemStack(net.minecraft.world.item.Items.STICK));
        require(secondHost.craftingGrid().isEmpty() && secondHost.crystalInventory().isEmpty()
                && secondHost.wandInventory().isEmpty(), "Wireless terminals share inventory");
        var ops = helper.getLevel().registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
        var saved = ItemStack.CODEC.encodeStart(ops, first).getOrThrow();
        var reloaded = ItemStack.CODEC.parse(ops, saved).getOrThrow();
        var restored = new thaumicenergistics_ce.item.WirelessArcaneCraftingTerminalMenuHost(item, player,
                appeng.menu.locator.MenuLocators.forStack(reloaded), (p, menu) -> {});
        require(restored.craftingGrid().getStackInSlot(0).getCount() == 3
                && restored.crystalInventory().getStackInSlot(0).getCount() == 2
                && restored.wandInventory().getStackInSlot(0).is(net.minecraft.world.item.Items.STICK), "Wireless inventory NBT loss");
        require(!restored.getLinkStatus().connected() && restored.getActionableNode() == null
                && restored.getInventory().getAvailableStacks().isEmpty(), "Offline host exposes network");
        var menu = new thaumicenergistics_ce.menu.MenuArcaneCraftingTerminal(
                thaumicenergistics_ce.init.ModMenuTypes.WIRELESS_ARCANE_CRAFTING_TERMINAL.get(), 2, player.getInventory(), restored);
        require(menu.craftingInventory().getStackInSlot(0).getCount() == 3 && !menu.canCraft(), "Offline menu inventory or crafting gate");
        menu.essentiaAccessEnabled = false;
        menu.updateUpgradeSlotVisibility();
        var upgrades = menu.getSlots(appeng.menu.SlotSemantics.UPGRADE);
        long disabledCount = upgrades.stream().filter(net.minecraft.world.inventory.Slot::isActive).count();
        require(disabledCount == upgrades.size() - 1, "Disabled essentia access left an extra upgrade slot");
        menu.essentiaAccessEnabled = true;
        menu.updateUpgradeSlotVisibility();
        require(upgrades.stream().allMatch(net.minecraft.world.inventory.Slot::isActive), "Enabling essentia access did not restore slot");
        menu.essentiaAccessEnabled = false;
        restored.getUpgrades().setItemDirect(0, new ItemStack(ModItems.ESSENTIA_ACCESS_CARD.get()));
        menu.updateUpgradeSlotVisibility();
        require(upgrades.getFirst().isActive(), "Disabling access hid an installed card");
        var accessCard = new ItemStack(ModItems.ESSENTIA_ACCESS_CARD.get());
        var accessSetting = thaumicenergistics_ce.ThEConfig.ARCANE_ESSENTIA_ACCESS;
        boolean previousSetting = accessSetting.get();
        try {
            accessSetting.set(false);
            require(!upgrades.getFirst().mayPlace(accessCard), "Disabled access accepted a card without global slot mixin");
            require(upgrades.getFirst().mayPickup(player), "Disabled access prevents removing an installed card");
            accessSetting.set(true);
            restored.getUpgrades().setItemDirect(0, ItemStack.EMPTY);
            require(upgrades.getFirst().mayPlace(accessCard), "Enabled access rejected its card");
        } finally {
            accessSetting.set(previousSetting);
        }
        menu.removed(player);
        require(restored.craftingGrid().getStackInSlot(0).getCount() == 3, "Closing menu discarded inventory");
        System.out.println("PORT PASS wireless item isolation, NBT, offline inventory and craft gate");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void providerPersistence(GameTestHelper helper) {
        var level = helper.getLevel();
        var registries = level.registryAccess();
        var key = AEssentiaKey.of(ResourceLocation.fromNamespaceAndPath("thaumaturge", "aer"));
        var aspect = key.resolveAspect();
        require(aspect != null, "Aer registry missing");
        int tested = 0;
        for (var entry : BuiltInRegistries.BLOCK.entrySet()) {
            var id = entry.getKey().location();
            if (!java.util.Set.of("ae2", "extendedae", "extendedae_plus", "advanced_ae", "ae2lt").contains(id.getNamespace())
                    || !id.getPath().contains("provider")) continue;
            var block = entry.getValue();
            if (!(block instanceof EntityBlock factory)) continue;
            var state = block.defaultBlockState();
            var be = factory.newBlockEntity(BlockPos.ZERO, state);
            if (be == null) continue;
            be.setLevel(level);
            var provider = PatternProviderAddonCompat.provider(be);
            if (provider == null) continue;
            var cards = provider.essentiaCardInventory();
            require(cards.size() == 1, id + ": card slot count");
            require(cards.insertItem(0, new ItemStack(ModItems.ESSENTIA_ACCESS_CARD.get()), false).isEmpty(), id + ": card rejected");
            require(!cards.insertItem(0, new ItemStack(ModItems.ESSENTIA_ACCESS_CARD.get()), false).isEmpty(), id + ": accepted duplicate card");
            require(provider.essentiaEnabled(), id + ": card did not enable access");
            if (be instanceof appeng.helpers.patternprovider.PatternProviderLogicHost host) {
                var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
                var menu = new appeng.menu.implementations.PatternProviderMenu(
                        appeng.menu.implementations.PatternProviderMenu.TYPE, 3, player.getInventory(), host);
                for (var slot : menu.getSlots(appeng.menu.SlotSemantics.UPGRADE)) {
                    if (((appeng.menu.slot.AppEngSlot) slot).getInventory() != cards) {
                        require(!slot.mayPlace(new ItemStack(ModItems.ESSENTIA_ACCESS_CARD.get())),
                                id + ": regular upgrade slot accepted dedicated card");
                    }
                }
                menu.removed(player);
            }
            var returns = provider.essentiaReturnStorage();
            int simulated = returns.insert(aspect, 17, true);
            require(simulated == 17 && returns.amount(aspect) == 0, id + ": preview mutated buffer");
            require(returns.insert(aspect, 17, false) == 17 && returns.amount(aspect) == 17, id + ": return insert");
            var saved = be.saveWithFullMetadata(registries);
            var restored = BlockEntity.loadStatic(BlockPos.ZERO, state, saved, registries);
            require(restored != null, id + ": NBT reload failed");
            restored.setLevel(level);
            var after = PatternProviderAddonCompat.provider(restored);
            require(after != null && after.essentiaCardInventory().getStackInSlot(0).getCount() == 1, id + ": card lost on reload");
            require(after.essentiaReturnStorage().amount(aspect) == 17, id + ": return buffer lost on reload");
            after.essentiaCardInventory().extractItem(0, 1, false);
            require(after.essentiaReturnStorage().insert(aspect, 1, false) == 0, id + ": stale adapter accepts without card");
            require(after.essentiaReturnStorage().amount(aspect) == 17, id + ": removal deleted buffered essentia");
            tested++;
            System.out.println("PORT PASS provider: " + id);
        }
        require(tested >= 1, "No provider tested");
        System.out.println("PORT PASS provider count: " + tested);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void busRecoveryPersistence(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        var key = AEssentiaKey.of(ResourceLocation.fromNamespaceAndPath("thaumaturge", "aer"));
        var before = new EssentiaBusBuffer(() -> {});
        before.store(key, 13, 10);
        var tag = new net.minecraft.nbt.CompoundTag();
        before.write(tag, registries);
        var after = new EssentiaBusBuffer(() -> {});
        after.read(tag, registries);
        require(after.held() != null && after.held().what().equals(key) && after.held().amount() == 13, "Bus recovery NBT");
        after.remove(5);
        require(after.held().amount() == 8, "Bus recovery partial removal");
        for (var item : java.util.List.of(AEParts.IMPORT_BUS.asItem(), AEParts.EXPORT_BUS.asItem(), AEParts.STORAGE_BUS.asItem())) {
            var part = ((IPartItem<?>) item).createPart();
            require(part instanceof appeng.api.upgrades.IUpgradeableObject, "Bus is not upgradeable");
            int slots = ((appeng.api.upgrades.IUpgradeableObject) part).getUpgrades().size();
            require(slots == (item == AEParts.STORAGE_BUS.asItem() ? 5 : 6), "Wrong bus slot count: " + slots);
        }
        System.out.println("PORT PASS bus buffer NBT and native upgrade slot counts");
        helper.succeed();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
