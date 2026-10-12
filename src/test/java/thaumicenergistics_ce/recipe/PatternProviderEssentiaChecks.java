package thaumicenergistics_ce.recipe;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderTarget;
import appeng.menu.SlotSemantics;
import appeng.menu.implementations.PatternProviderMenu;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aspect.TTAspects;
import com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities;
import com.leclowndu93150.thaumaturge.registry.TTBlocks;
import java.util.ArrayList;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import thaumicenergistics_ce.integration.ae2.patternprovider.EssentiaPatternProvider;
import thaumicenergistics_ce.part.EssentiaNeighbour;
import thaumicenergistics_ce.registry.TECEItems;

final class PatternProviderEssentiaChecks {
    static void run(MinecraftServer server) {
        var level = server.overworld();
        var player = FakePlayerFactory.getMinecraft(level);
        var pos = new BlockPos(24, 240, 24);
        var jarPos = pos.below();
        var partPos = pos.east();
        require(level.getBlockState(pos).isAir() && level.getBlockState(jarPos).isAir()
                && level.getBlockState(partPos).isAir(), "Provider fixture occupied");
        var aspect = server.registryAccess().lookupOrThrow(IAspect.REGISTRY_KEY).getOrThrow(TTAspects.IGNIS);
        var key = AEssentiaKey.of(aspect);
        try {
            level.setBlock(pos, AEBlocks.PATTERN_PROVIDER.block().defaultBlockState(), 3);
            level.setBlock(jarPos, TTBlocks.JAR_NORMAL.get().defaultBlockState(), 3);
            var provider = (PatternProviderBlockEntity) level.getBlockEntity(pos);
            var logic = provider.getLogic();
            var extension = (EssentiaPatternProvider) logic;
            var inv = extension.essentiaCardInventory();
            var targetMethod = PatternProviderLogic.class.getDeclaredMethod("findAdapter", Direction.class);
            targetMethod.setAccessible(true);
            var target = (PatternProviderTarget) targetMethod.invoke(logic, Direction.DOWN);
            var jar = EssentiaNeighbour.find(level, jarPos, Direction.UP);
            require(target.insert(key, 12, Actionable.MODULATE) == 0, "Card-free provider sent essentia");
            require(logic.getReturnInv().insert(key, 12, Actionable.MODULATE, IActionSource.empty()) == 0,
                    "Native return inventory bypassed card permission");
            require(!inv.insertItem(0, new ItemStack(Items.STONE), false).isEmpty(), "Non-card item accepted");
            var two = new ItemStack(TECEItems.ESSENTIA_ACCESS_CARD.get(), 2);
            require(inv.insertItem(0, two, false).getCount() == 1, "Card slot accepted more than one card");
            PatternProviderConfigChecks.run(provider, target, aspect);
            require(target.insert(key, 12, Actionable.SIMULATE) == 12 && jar.amount(aspect) == 0,
                    "Send simulation changed jar contents");
            require(target.insert(key, 12, Actionable.MODULATE) == 12 && jar.amount(aspect) == 12,
                    "Provider did not send to a live jar");
            require(target.containsPatternInput(Set.of(key.dropSecondary())), "Blocking mode missed essentia inputs");
            // Exercise AE2's complete pushPattern path, not only the target adapter.
            var nodeField = PatternProviderLogic.class.getDeclaredField("mainNode");
            nodeField.setAccessible(true);
            var originalNode = nodeField.get(logic);
            var activeNode = java.lang.reflect.Proxy.newProxyInstance(
                    appeng.api.networking.IManagedGridNode.class.getClassLoader(),
                    new Class<?>[] {appeng.api.networking.IManagedGridNode.class},
                    (proxy, method, args) -> method.getName().equals("isActive") ? true
                            : method.invoke(originalNode, args));
            nodeField.set(logic, activeNode);
            try {
                var encoded = appeng.api.crafting.PatternDetailsHelper.encodeProcessingPattern(
                        java.util.List.of(new appeng.api.stacks.GenericStack(key, 12)),
                        java.util.List.of(new appeng.api.stacks.GenericStack(AEItemKey.of(Items.STONE), 1)));
                logic.getPatternInv().setItemDirect(0, encoded);
                var pattern = logic.getAvailablePatterns().iterator().next();
                var supplied = new KeyCounter();
                supplied.add(key, 12);
                var input = new KeyCounter[] {supplied};
                inv.extractItem(0, 1, false);
                require(!logic.pushPattern(pattern, input) && supplied.get(key) == 12,
                        "Card-free pattern push consumed inputs");
                inv.insertItem(0, new ItemStack(TECEItems.ESSENTIA_ACCESS_CARD.get()), false);
                int before = jar.amount(aspect);
                require(logic.pushPattern(pattern, input) && jar.amount(aspect) == before + 12
                        && !logic.isBusy(),
                        "Complete pattern push did not deliver exactly one batch");
                int added = thaumicenergistics_ce.util.ThETransaction.apply(tx -> jar.insert(aspect, 100000, tx));
                require(added > 0, "Jar capacity fixture could not fill");
                thaumicenergistics_ce.util.ThETransaction.apply(tx -> jar.extract(aspect, 1, tx));
                require(logic.pushPattern(pattern, input) && logic.isBusy(),
                        "Partial delivery did not keep the undelivered batch queued");
                var queued = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, server.registryAccess());
                logic.writeToNBT(queued);
                var pending = queued.buildResult().getListOrEmpty("sendList");
                require(pending.size() == 1, "Partial delivery queue was not saved");
                var retry = PatternProviderLogic.class.getDeclaredMethod("sendStacksOut");
                retry.setAccessible(true);
                inv.extractItem(0, 1, false);
                thaumicenergistics_ce.util.ThETransaction.apply(tx -> jar.extract(aspect, 100000, tx));
                require(!(boolean) retry.invoke(logic) && logic.isBusy(),
                        "Removing card bypassed permission for queued delivery");
                inv.insertItem(0, new ItemStack(TECEItems.ESSENTIA_ACCESS_CARD.get()), false);
                require((boolean) retry.invoke(logic) && !logic.isBusy() && jar.amount(aspect) == 11,
                        "Retry lost or duplicated the eleven undelivered essentia");
            } finally {
                nodeField.set(logic, originalNode);
            }
            var menu = new PatternProviderMenu(PatternProviderMenu.TYPE, 0, player.getInventory(), provider);
            require(!menu.getSlots(SlotSemantics.UPGRADE).isEmpty(), "Right sidebar card slot missing");
            var cardSlot = (appeng.menu.slot.AppEngSlot) menu.getSlots(SlotSemantics.UPGRADE).stream()
                    .filter(slot -> slot.getItem().is(TECEItems.ESSENTIA_ACCESS_CARD.get())).findFirst().orElseThrow();
            require(cardSlot.getIcon() == appeng.util.Icon.BACKGROUND_UPGRADE, "Empty card slot icon missing");
            var playerSlot = menu.slots.stream().filter(menu::isPlayerSideSlot).findFirst().orElseThrow();
            var previousPlayerStack = playerSlot.getItem().copy();
            try {
                inv.extractItem(0, 1, false);
                playerSlot.set(new ItemStack(TECEItems.ESSENTIA_ACCESS_CARD.get(), 2));
                menu.quickMoveStack(player, playerSlot.index);
                require(cardSlot.getItem().getCount() == 1 && playerSlot.getItem().getCount() == 1,
                        "Shift-click did not insert exactly one card");
                menu.quickMoveStack(player, playerSlot.index);
                require(cardSlot.getItem().getCount() == 1 && playerSlot.getItem().getCount() == 1,
                        "Shift-click into a full card slot swallowed or duplicated a card");
                playerSlot.set(ItemStack.EMPTY);
                int cardsBefore = player.getInventory().countItem(TECEItems.ESSENTIA_ACCESS_CARD.get());
                menu.quickMoveStack(player, cardSlot.index);
                require(cardSlot.getItem().isEmpty()
                        && player.getInventory().countItem(TECEItems.ESSENTIA_ACCESS_CARD.get()) == cardsBefore + 1,
                        "Shift-click did not return the installed card to the player");
                // Remove the returned fixture card, then restore the installed test card.
                for (var slot : menu.slots) {
                    if (menu.isPlayerSideSlot(slot) && slot.getItem().is(TECEItems.ESSENTIA_ACCESS_CARD.get())) {
                        slot.remove(1);
                        break;
                    }
                }
                inv.insertItem(0, new ItemStack(TECEItems.ESSENTIA_ACCESS_CARD.get()), false);
            } finally {
                playerSlot.set(previousPlayerStack);
            }
            var receive = level.getCapability(EssentiaCapabilities.STORAGE, pos, Direction.UP);
            require(receive != null, "Provider essentia return capability missing");
            try (var preview = Transaction.openRoot()) {
                require(receive.insert(aspect, 9, preview) == 9, "Return insertion failed");
            }
            require(logic.getReturnInv().isEmpty(), "Return preview consumed essentia");
            long beforePreview = receive.contentRevision();
            try (var outer = Transaction.openRoot()) {
                try (var nested = Transaction.open(outer)) {
                    receive.insert(aspect, 9, nested);
                    nested.commit();
                }
            }
            require(logic.getReturnInv().isEmpty() && receive.contentRevision() == beforePreview,
                    "Nested return commit escaped outer rollback");
            try (var tx = Transaction.openRoot()) {
                receive.insert(aspect, 9, tx);
                tx.commit();
            }
            var saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, server.registryAccess());
            logic.writeToNBT(saved);
            var drops = new ArrayList<ItemStack>();
            logic.addDrops(drops);
            require(drops.stream().filter(s -> s.is(TECEItems.ESSENTIA_ACCESS_CARD.get()))
                    .mapToInt(ItemStack::getCount).sum() == 1, "Provider card drop duplicated/lost");
            logic.clearContent();
            require(inv.isEmpty(), "Clearing provider retained its card");
            logic.readFromNBT(TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(), saved.buildResult()));
            require(inv.getStackInSlot(0).getCount() == 1 && receive.amount(aspect) == 9,
                    "Card/return buffer did not persist");
            inv.extractItem(0, 1, false);
            require(target.insert(key, 1, Actionable.MODULATE) == 0, "Card removal left cached send permission");
            try (var tx = Transaction.openRoot()) {
                require(receive.insert(aspect, 1, tx) == 0, "Card removal left cached receive permission");
            }
            long[] returned = {0};
            MEStorage network = new MEStorage() {
                @Override public net.minecraft.network.chat.Component getDescription() {
                    return net.minecraft.network.chat.Component.literal("Provider return fixture");
                }
                @Override public void getAvailableStacks(KeyCounter out) {}
                @Override public long insert(appeng.api.stacks.AEKey what, long amount, Actionable mode, IActionSource src) {
                    long accepted = Math.min(4, amount);
                    if (mode == Actionable.MODULATE) returned[0] += accepted;
                    return accepted;
                }
            };
            long beforeReturn = receive.contentRevision();
            logic.getReturnInv().injectIntoNetwork(network, IActionSource.empty(), stack -> {});
            require(returned[0] == 4 && receive.amount(aspect) == 5,
                    "Partial return lost/duplicated contents after card removal");
            require(receive.contentRevision() > beforeReturn, "Returning to network left stale cached contents");
            require(logic.getReturnInv().insert(AEItemKey.of(Items.STONE), 1, Actionable.MODULATE,
                    IActionSource.empty()) == 1, "Card gate broke item returns");
            level.setBlock(partPos, AEBlocks.CABLE_BUS.block().defaultBlockState(), 3);
            var partHost = (appeng.api.parts.IPartHost) level.getBlockEntity(partPos);
            var part = (appeng.parts.crafting.PatternProviderPart) partHost.addPart(AEParts.PATTERN_PROVIDER.get(),
                    Direction.UP, player);
            require((Object) part.getLogic() instanceof EssentiaPatternProvider, "Panel provider logic missing extension");
            require(level.getCapability(EssentiaCapabilities.STORAGE, partPos, Direction.UP) != null,
                    "Panel provider return capability missing");
            var panelExtension = (EssentiaPatternProvider) part.getLogic();
            panelExtension.essentiaCardInventory().insertItem(0,
                    new ItemStack(TECEItems.ESSENTIA_ACCESS_CARD.get()), false);
            var stalePanelReceive = panelExtension.essentiaReturnStorage();
            partHost.removePartFromSide(Direction.UP);
            try (var tx = Transaction.openRoot()) {
                require(stalePanelReceive.insert(aspect, 1, tx) == 0,
                        "Removed panel accepted essentia through a stale capability");
            }
            inv.insertItem(0, new ItemStack(TECEItems.ESSENTIA_ACCESS_CARD.get()), false);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            try (var tx = Transaction.openRoot()) {
                require(receive.insert(aspect, 1, tx) == 0,
                        "Removed block accepted essentia through a stale capability");
            }
            System.out.println("[pattern-provider-essentia] PASS: full processing-pattern push, partial send queue/retry, block/part card slot, live simulation/commit, blocking mode, receive/nested rollback, persistence/drops, card removal, partial network return, ordinary items");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        } finally {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(jarPos, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(partPos, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
