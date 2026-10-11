package thaumicenergistics_ce.recipe;

import thaumicenergistics_ce.integration.ae2.bus.EssentiaBusAccess;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.SchedulingMode;
import appeng.api.networking.IGrid;
import appeng.api.behaviors.StackTransferContext;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.parts.IPartHost;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.menu.SlotSemantics;
import appeng.menu.implementations.IOBusMenu;
import appeng.menu.implementations.StorageBusMenu;
import appeng.parts.automation.UpgradeablePart;
import appeng.parts.automation.StackWorldBehaviors;
import appeng.parts.automation.ExportBusPart;
import appeng.parts.automation.ImportBusPart;
import appeng.parts.misc.InterfacePart;
import appeng.parts.storagebus.StorageBusPart;
import appeng.util.prioritylist.IPartitionList;
import appeng.util.SettingsFrom;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aspect.TTAspects;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import com.leclowndu93150.thaumaturge.registry.TTBlocks;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import thaumicenergistics_ce.integration.ae2.bus.EssentiaBusTransfer;
import thaumicenergistics_ce.integration.ae2.bus.EssentiaBusBuffer;
import thaumicenergistics_ce.integration.ae2.bus.EssentiaBusHost;
import thaumicenergistics_ce.integration.ae2.bus.EssentiaBusStorage;
import thaumicenergistics_ce.config.ThEConfig;
import thaumicenergistics_ce.registry.ModItems;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import thaumicenergistics_ce.integration.ae2.AEssentiaKeyType;
import thaumicenergistics_ce.integration.ae2.EssentiaMEStorage;
import thaumicenergistics_ce.network.EssentiaBusConfigPayload;
import thaumicenergistics_ce.part.EssentiaNeighbour;
import thaumicenergistics_ce.util.ThETransaction;

public final class EssentiaBusChecks {
    private EssentiaBusChecks() {}

    public static void run(MinecraftServer server) {
        var aspects = server.registryAccess().lookupOrThrow(IAspect.REGISTRY_KEY);
        var ignis = aspects.getOrThrow(TTAspects.IGNIS);
        var aqua = aspects.getOrThrow(TTAspects.AQUA);
        boolean enabled = ThEConfig.BUS_ACCESS.get();
        double power = ThEConfig.BUS_POWER.get();
        try {
            ThEConfig.BUS_ACCESS.set(true);
            ThEConfig.BUS_POWER.set(10.0);
            transferChecks(ignis, aqua);
            recoveryChecks(server, ignis);
            worldChecks(server, ignis);
        } finally {
            ThEConfig.BUS_ACCESS.set(enabled);
            ThEConfig.BUS_POWER.set(power);
        }
        System.out.println("[essentia-buses] PASS: sixth slot, interface rejection, menu packets, import/export filters, energy/capacity, recovery persistence and drops, restored selection, native export budget and live jar mount");
    }

    private static void transferChecks(Holder<IAspect> ignis, Holder<IAspect> aqua) {
        var fire = AEssentiaKey.of(ignis);
        var water = AEssentiaKey.of(aqua);
        var tank = new Tank(64, AspectList.EMPTY.add(ignis, 20).add(aqua, 20));
        var network = new Network();
        var energy = new Energy(1000);
        var filterBuilder = IPartitionList.builder();
        filterBuilder.add(fire);
        var filter = filterBuilder.build();
        var context = context(network, energy, 1, filter, IActionSource.empty());
        require(EssentiaBusTransfer.importFrom(context, tank, new EssentiaBusBuffer(() -> {})), "Import did no work");
        require(tank.amount(ignis) == 12 && tank.amount(aqua) == 20 && network.amount(fire) == 8,
                "Import rate/filter failed");
        require(energy.amount == 920 && context.getOperationsRemaining() == 0, "Import power/operations failed");

        context = context(network, energy, 1, filter, IActionSource.empty());
        context.setInverted(true);
        EssentiaBusTransfer.importFrom(context, tank, new EssentiaBusBuffer(() -> {}));
        require(tank.amount(aqua) == 12 && tank.amount(ignis) == 12, "Inverter imported listed aspect");

        var noPower = new Energy(0);
        require(!EssentiaBusTransfer.importFrom(context(network, noPower, 4, filter, IActionSource.empty()), tank, new EssentiaBusBuffer(() -> {})),
                "Import moved without power");
        network.limit = 0;
        require(!EssentiaBusTransfer.importFrom(context(network, energy, 4, filter, IActionSource.empty()), tank, new EssentiaBusBuffer(() -> {})),
                "Import consumed source into full network");
        network.limit = Long.MAX_VALUE;
        var littlePower = new Energy(25);
        EssentiaBusTransfer.importFrom(context(network, littlePower, 4, filter, IActionSource.empty()), tank, new EssentiaBusBuffer(() -> {}));
        require(littlePower.amount == 5 && tank.amount(ignis) == 10, "Import did not stop at affordable amount");

        var extractOnly = new Tank(64, AspectList.EMPTY.add(ignis, 20));
        extractOnly.acceptsInsert = false;
        var partialNetwork = new Network();
        partialNetwork.actualInsertLimit = 3;
        var partialBuffer = new EssentiaBusBuffer(() -> {});
        EssentiaBusTransfer.importFrom(context(partialNetwork, energy, 1, filter, IActionSource.empty()), extractOnly, partialBuffer);
        require(partialNetwork.amount(fire) == 3 && extractOnly.amount(ignis) == 12
                        && partialBuffer.held().amount() == 5,
                "Partial import lost essentia from extract-only source");

        var destination = new Tank(5, AspectList.EMPTY);
        long exported = EssentiaBusTransfer.exportTo(context(network, energy, 4, filter, IActionSource.empty()),
                destination, fire, 32, new EssentiaBusBuffer(() -> {}));
        require(exported == 5 && destination.amount(ignis) == 5, "Export overflowed target");
        require(EssentiaBusTransfer.exportTo(context(network, noPower, 1, filter, IActionSource.empty()),
                new Tank(64, AspectList.EMPTY), fire, 8, new EssentiaBusBuffer(() -> {})) == 0, "Export moved without power");

        var partialSource = new Network();
        partialSource.amounts.put(fire, 20L);
        partialSource.actualExtractLimit = 3;
        var retryDestination = new Tank(64, AspectList.EMPTY);
        exported = EssentiaBusTransfer.exportTo(context(partialSource, energy, 1, filter, IActionSource.empty()),
                retryDestination, fire, 8, new EssentiaBusBuffer(() -> {}));
        require(exported == 3 && retryDestination.amount(ignis) == 3 && partialSource.amount(fire) == 17,
                "Partial extraction duplicated target contents");
        ThEConfig.BUS_POWER.set(0.0);
        require(EssentiaBusTransfer.exportTo(context(partialSource, noPower, 1, filter, IActionSource.empty()),
                retryDestination, fire, 8, new EssentiaBusBuffer(() -> {})) == 3, "Zero-cost config did not work");
        ThEConfig.BUS_POWER.set(10.0);
        require(network.amount(water) == 8, "Unrelated aspect changed during export");

        var mountedTarget = new Tank(3, AspectList.EMPTY);
        var transactionalSource = new Tank(64, AspectList.EMPTY.add(ignis, 20));
        var mountedBuffer = new EssentiaBusBuffer(() -> {});
        transactionalSource.acceptsInsert = false;
        EssentiaBusTransfer.importFrom(context(transactionalNetwork(mountedTarget), energy,
                1, filter, IActionSource.empty()), transactionalSource, mountedBuffer);
        require(mountedTarget.amount(ignis) == 3 && transactionalSource.amount(ignis) == 12
                        && mountedBuffer.held().amount() == 5,
                "Import trial rolled back another mounted container");
        var mountedSource = new Tank(64, AspectList.EMPTY.add(ignis, 3));
        var transactionalTarget = new Tank(64, AspectList.EMPTY);
        EssentiaBusTransfer.exportTo(context(transactionalNetwork(mountedSource), energy,
                1, filter, IActionSource.empty()), transactionalTarget, fire, 8, new EssentiaBusBuffer(() -> {}));
        require(mountedSource.amount(ignis) == 0 && transactionalTarget.amount(ignis) == 3,
                "Export trial rolled back another mounted container");
    }

    private static void recoveryChecks(MinecraftServer server, Holder<IAspect> aspect) {
        var key = AEssentiaKey.of(aspect);
        var filterBuilder = IPartitionList.builder();
        filterBuilder.add(key);
        var filter = filterBuilder.build();
        require(key.getAmountPerOperation() == 1, "Bus factor changed terminal storage billing");
        var billedNetwork = new Network();
        billedNetwork.amounts.put(key, 20L);
        var billedEnergy = new Energy(100);
        require(appeng.api.storage.StorageHelper.poweredExtraction(billedEnergy, billedNetwork, key, 8,
                IActionSource.empty()) == 8 && billedEnergy.amount == 92,
                "Terminal extraction no longer uses one AE per point");

        var noActualPower = new Energy(100);
        noActualPower.actualLimit = 0;
        var source = new Tank(64, AspectList.EMPTY.add(aspect, 20));
        var network = new Network();
        var buffer = new EssentiaBusBuffer(() -> {});
        require(!EssentiaBusTransfer.importFrom(context(network, noActualPower, 1, filter,
                        IActionSource.empty()), source, buffer)
                        && source.amount(aspect) == 20 && network.amount(key) == 0 && buffer.isEmpty(),
                "Import trusted simulated power instead of actual payment");

        var energy = new Energy(10000);
        network.actualInsertLimit = 0;
        require(EssentiaBusTransfer.importFrom(context(network, energy, 1, filter, IActionSource.empty()),
                source, buffer), "Rejected insertion did not retain source in bus");
        require(source.amount(aspect) == 12 && buffer.held().amount() == 8 && network.amount(key) == 0,
                "Rejected import lost or duplicated essentia");
        require(!EssentiaBusTransfer.importFrom(context(network, energy, 1, filter, IActionSource.empty()),
                source, buffer) && source.amount(aspect) == 12, "Pending recovery did not stop further withdrawal");
        network.actualInsertLimit = Long.MAX_VALUE;
        require(buffer.returnToNetwork(network, IActionSource.empty(), 3) == 3
                && buffer.held().amount() == 5 && network.amount(key) == 3, "Recovery ignored its transfer budget");
        require(buffer.returnToNetwork(network, IActionSource.empty()) == 5 && buffer.isEmpty()
                && source.amount(aspect) + network.amount(key) == 20, "Recovery did not conserve contents");

        var rejectedRefund = new Network();
        rejectedRefund.amounts.put(key, 20L);
        rejectedRefund.actualInsertLimit = 0;
        var shortTarget = new Tank(64, AspectList.EMPTY);
        shortTarget.secondInsertLimit = 3;
        var exportBuffer = new EssentiaBusBuffer(() -> {});
        require(EssentiaBusTransfer.exportTo(context(rejectedRefund, energy, 1, filter, IActionSource.empty()),
                shortTarget, key, 8, exportBuffer) == 3, "Actual partial target acceptance was ignored");
        require(rejectedRefund.amount(key) == 12 && shortTarget.amount(aspect) == 3
                && exportBuffer.held().amount() == 5, "Rejected export refund lost essentia");
        rejectedRefund.actualInsertLimit = Long.MAX_VALUE;
        exportBuffer.returnToNetwork(rejectedRefund, IActionSource.empty());
        require(rejectedRefund.amount(key) + shortTarget.amount(aspect) == 20 && exportBuffer.isEmpty(),
                "Export refund duplicated contents");

        var failingNetwork = new Network();
        failingNetwork.failInsert = true;
        var failureSource = new Tank(64, AspectList.EMPTY.add(aspect, 20));
        var failureBuffer = new EssentiaBusBuffer(() -> {});
        EssentiaBusTransfer.importFrom(context(failingNetwork, energy, 1, filter, IActionSource.empty()),
                failureSource, failureBuffer);
        require(failureBuffer.held().amount() == 8 && failureSource.amount(aspect) == 12,
                "Network exception discarded bus contents");
        failingNetwork.failInsert = false;
        failureBuffer.returnToNetwork(failingNetwork, IActionSource.empty());
        require(failingNetwork.amount(key) + failureSource.amount(aspect) == 20,
                "Network recovery after exception duplicated contents");

        var failureExportNetwork = new Network();
        failureExportNetwork.amounts.put(key, 20L);
        failureExportNetwork.actualInsertLimit = 0;
        var throwingTarget = new Tank(64, AspectList.EMPTY);
        throwingTarget.failSecondInsert = true;
        var failureExportBuffer = new EssentiaBusBuffer(() -> {});
        require(EssentiaBusTransfer.exportTo(context(failureExportNetwork, energy, 1, filter, IActionSource.empty()),
                throwingTarget, key, 8, failureExportBuffer) == 0,
                "Target exception reported a successful export");
        require(failureExportBuffer.held().amount() == 8 && throwingTarget.amount(aspect) == 0
                && failureExportNetwork.amount(key) == 12, "Target exception lost or duplicated essentia");
        failureExportNetwork.actualInsertLimit = Long.MAX_VALUE;
        failureExportBuffer.returnToNetwork(failureExportNetwork, IActionSource.empty());
        require(failureExportNetwork.amount(key) == 20 && failureExportBuffer.isEmpty(),
                "Failed target export could not return to network");

        try (var transaction = Transaction.openRoot()) {
            require(!EssentiaBusTransfer.importFrom(context(network, energy, 1, filter, IActionSource.empty()),
                    source, buffer), "Bus joined a parent transaction that could roll back only one side");
        }
        require(source.amount(aspect) == 12 && network.amount(key) == 8,
                "Parent cancellation changed resources");
        var refundNetwork = new Network();
        refundNetwork.amounts.put(key, 20L);
        refundNetwork.actualInsertLimit = 0;
        var refundTarget = new Tank(64, AspectList.EMPTY);
        refundTarget.secondInsertLimit = 3;
        var refundEnergy = new Energy(1000);
        var service = gridEnergy(refundEnergy);
        var refundBuffer = new EssentiaBusBuffer(() -> {});
        require(EssentiaBusTransfer.exportTo(context(refundNetwork, service, 1, filter, IActionSource.empty()),
                refundTarget, key, 8, refundBuffer) == 3 && refundEnergy.amount == 920,
                "Export did not reserve energy for pending contents");
        var saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, server.registryAccess());
        refundBuffer.write(saved);
        var restored = new EssentiaBusBuffer(() -> {});
        restored.read(TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(), saved.buildResult()));
        refundNetwork.actualInsertLimit = Long.MAX_VALUE;
        ThEConfig.BUS_POWER.set(20.0);
        try {
            require(restored.returnToNetwork(refundNetwork, IActionSource.empty(), Long.MAX_VALUE, service) == 5
                    && refundEnergy.amount == 970, "Delayed refund used new config instead of original paid fee");
        } finally {
            ThEConfig.BUS_POWER.set(10.0);
        }
        var committedSource = new Tank(64, AspectList.EMPTY.add(aspect, 20));
        committedSource.failRootCommit = true;
        var committedNetwork = new Network();
        require(EssentiaBusTransfer.importFrom(context(committedNetwork, new Energy(1000),
                1, filter, IActionSource.empty()), committedSource, new EssentiaBusBuffer(() -> {}))
                && committedSource.amount(aspect) + committedNetwork.amount(key) == 20,
                "Extraction commit callback failure lost contents");
        var committedTarget = new Tank(64, AspectList.EMPTY);
        committedTarget.failRootCommit = true;
        require(EssentiaBusTransfer.exportTo(context(committedNetwork, new Energy(1000),
                1, filter, IActionSource.empty()), committedTarget, key, 8,
                new EssentiaBusBuffer(() -> {})) == 8
                && committedTarget.amount(aspect) + committedNetwork.amount(key) == 8,
                "Insertion commit callback failure duplicated contents");

        var changingPriceNetwork = new Network();
        changingPriceNetwork.amounts.put(key, 20L);
        changingPriceNetwork.actualExtractLimit = 3;
        changingPriceNetwork.afterExtract = () -> ThEConfig.BUS_POWER.set(20.0);
        var changingPriceEnergy = new Energy(1000);
        try {
            require(EssentiaBusTransfer.exportTo(context(changingPriceNetwork, gridEnergy(changingPriceEnergy),
                    1, filter, IActionSource.empty()), new Tank(64, AspectList.EMPTY), key, 8,
                    new EssentiaBusBuffer(() -> {})) == 3 && changingPriceEnergy.amount == 970,
                    "Config reload during transfer changed the price of already-reserved power");
        } finally {
            ThEConfig.BUS_POWER.set(10.0);
        }
        System.out.println("[essentia-bus-fixes] PASS: original terminal billing, actual payment, rejected refunds, failed delivery, pending recovery and parent cancellation");
    }

    private static void worldChecks(MinecraftServer server, Holder<IAspect> aspect) {
        var level = server.overworld();
        var player = FakePlayerFactory.getMinecraft(level);
        var target = new BlockPos(8, 240, 8);
        var hostPos = target.above();
        require(level.getBlockState(target).isAir() && level.getBlockState(hostPos).isAir(), "Fixture site occupied");
        level.getChunk(target);
        var key = AEssentiaKey.of(aspect);
        var energy = new Energy(10000);
        var network = new Network();
        var emptyFilter = IPartitionList.builder().build();
        try {
            level.setBlock(target, TTBlocks.JAR_NORMAL.get().defaultBlockState(), 3);
            var jar = EssentiaNeighbour.find(level, target, Direction.UP);
            require(jar != null && EssentiaNeighbour.find(level, target, Direction.NORTH) == null,
                    "Jar connection face ignored");
            ThETransaction.apply(tx -> jar.insert(aspect, 32, tx));
            for (var item : java.util.List.of(AEParts.IMPORT_BUS.get(), AEParts.EXPORT_BUS.get(), AEParts.STORAGE_BUS.get())) {
                level.setBlock(hostPos, AEBlocks.CABLE_BUS.block().defaultBlockState(), 3);
                var host = (IPartHost) level.getBlockEntity(hostPos);
                var bus = (UpgradeablePart) host.addPart(item, Direction.DOWN, player);
                int expectedSlots = bus instanceof StorageBusPart ? 5 : 6;
                require(bus != null && bus.getUpgrades().size() == expectedSlots, "Wrong bus upgrade slot count");
                if (bus instanceof StorageBusPart storageBus) { storageUpgradeMigration(server, storageBus); }
                var upgrades = bus.getUpgrades();
                int accessSlot = upgrades.size() - 1;
                var card = new ItemStack(ModItems.ESSENTIA_ACCESS_CARD.get());
                for (int slot = 0; slot < upgrades.size(); slot++) {
                    require(upgrades.insertItem(slot, card, false).isEmpty(),
                            "Access card rejected in upgrade slot " + slot);
                    require(EssentiaBusAccess.hasCard(bus), "Access card not detected in slot " + slot);
                    require(!upgrades.insertItem((slot + 1) % upgrades.size(), card, false).isEmpty(),
                            "Duplicate access card exceeded its limit");
                    upgrades.extractItem(slot, 1, false);
                    require(upgrades.insertItem(slot, new ItemStack(AEItems.CAPACITY_CARD.get()), false).isEmpty(),
                            "Native upgrade rejected in slot " + slot);
                    upgrades.extractItem(slot, 1, false);
                }
                require(upgrades.insertItem(0, new ItemStack(AEItems.CAPACITY_CARD.get()), false).isEmpty(),
                        "Original upgrade slot broken");
                if (bus instanceof appeng.parts.automation.ImportBusPart importBus) {
                    importBus.getKeyTypeSelection().setEnabled(AEssentiaKeyType.INSTANCE, false);
                }
                require(upgrades.insertItem(accessSlot, card, false).isEmpty(), "Access slot rejected card");
                if (bus instanceof appeng.parts.automation.ImportBusPart importBus) {
                    require(importBus.getKeyTypeSelection().isEnabled(AEssentiaKeyType.INSTANCE),
                            "Old import bus selection did not enable essentia on card insertion");
                }
                appeng.menu.implementations.UpgradeableMenu<?> menu = bus instanceof StorageBusPart storageBus
                        ? new StorageBusMenu(StorageBusMenu.TYPE, 73, player.getInventory(), storageBus)
                        : new IOBusMenu(item == AEParts.IMPORT_BUS.get() ? IOBusMenu.IMPORT_TYPE : IOBusMenu.EXPORT_TYPE,
                                73, player.getInventory(), (appeng.parts.automation.IOBusPart) bus);
                require(menu.getSlots(SlotSemantics.UPGRADE).size() == expectedSlots, "Native menu omitted sixth slot");
                var originalMenu = player.containerMenu;
                try {
                    player.containerMenu = menu;
                    new EssentiaBusConfigPayload(73, 0, key.getId()).handle(player);
                    var config = ((appeng.helpers.IConfigInvHost) bus).getConfig();
                    require(key.equals(config.getKey(0)), "JEI config payload did not store aspect");
                    new EssentiaBusConfigPayload(73, 62, key.getId()).handle(player);
                    require(config.getKey(62) == null, "Packet wrote locked config slot");
                    new EssentiaBusConfigPayload(74, 0, EssentiaBusConfigPayload.CLEAR).handle(player);
                    require(key.equals(config.getKey(0)), "Packet ignored container id");
                    new EssentiaBusConfigPayload(73, 0, EssentiaBusConfigPayload.CLEAR).handle(player);
                    require(config.getKey(0) == null, "Config clear failed");
                    upgrades.extractItem(accessSlot, 1, false);
                    new EssentiaBusConfigPayload(73, 0, key.getId()).handle(player);
                    require(config.getKey(0) == null, "Packet wrote config without access card");
                    upgrades.insertItem(accessSlot, card, false);
                } finally {
                    player.containerMenu = originalMenu;
                }
                if (bus instanceof StorageBusPart storageBus) {
                    var strategy = StackWorldBehaviors.createExternalStorageStrategies(level, target, Direction.UP)
                            .get(AEssentiaKeyType.INSTANCE);
                    var mounted = strategy.createWrapper(false, () -> {});
                    require(mounted != null, "Jar was not mounted");
                    KeyCounter available = new KeyCounter();
                    mounted.getAvailableStacks(available);
                    require(available.get(key) == jar.amount(aspect), "Mounted jar not enumerated");
                    long before = jar.amount(aspect);
                    require(mounted.extract(key, 2, Actionable.SIMULATE, IActionSource.empty()) == 2
                            && jar.amount(aspect) == before, "Storage simulation changed jar");
                    require(mounted.extract(key, 2, Actionable.MODULATE, IActionSource.empty()) == 2,
                            "Storage extract failed");
                    require(mounted.insert(key, 2, Actionable.MODULATE, IActionSource.empty()) == 2,
                            "Storage insert failed");
                    upgrades.extractItem(accessSlot, 1, false);
                    available.reset();
                    mounted.getAvailableStacks(available);
                    require(available.get(key) == 0 && mounted.extract(key, 2, Actionable.MODULATE,
                            IActionSource.empty()) == 0, "Old mounted wrapper remained active after card removal");
                    var nativeHandler = new appeng.me.storage.MEInventoryHandler(mounted);
                    nativeHandler.setVoidOverflow(true);
                    var guarded = new EssentiaBusStorage(nativeHandler, storageBus);
                    require(guarded.insert(key, 8, Actionable.MODULATE, IActionSource.empty()) == 0,
                            "Void card bypassed missing access card");
                    require(guarded.insert(AEItemKey.of(net.minecraft.world.item.Items.STONE), 1,
                            Actionable.MODULATE, IActionSource.empty()) == 1,
                            "Essentia gate changed native item void behavior");
                    upgrades.insertItem(accessSlot, card, false);
                    ThEConfig.BUS_ACCESS.set(false);
                    available.reset();
                    mounted.getAvailableStacks(available);
                    require(available.get(key) == 0, "Bus config switch did not hide mounted contents");
                    ThEConfig.BUS_ACCESS.set(true);
                    available.reset();
                    mounted.getAvailableStacks(available);
                    require(available.get(key) == before, "Re-enabling config did not restore mount");
                } else if (item == AEParts.IMPORT_BUS.get()) {
                    var strategy = StackWorldBehaviors.createImportFacade(level, target, Direction.UP, type -> true);
                    var context = context(network, energy, 1, emptyFilter, IActionSource.ofMachine(bus));
                    strategy.transfer(context);
                    require(network.amount(key) == 8, "Native import facade did not transfer essentia");
                    upgrades.extractItem(accessSlot, 1, false);
                    strategy.transfer(context(network, energy, 1, emptyFilter, IActionSource.ofMachine(bus)));
                    require(network.amount(key) == 8, "Import continued after card removal");
                    upgrades.insertItem(accessSlot, card, false);
                    persistenceChecks(server, (ImportBusPart) bus, host, key);
                } else {
                    nativeExportBudgetChecks(server, bus, host, key);
                    var strategy = StackWorldBehaviors.createExportFacade(level, target, Direction.UP);
                    var context = context(network, energy, 1, emptyFilter, IActionSource.ofMachine(bus));
                    require(strategy.transfer(context, key, 8) == 8 && network.amount(key) == 0,
                            "Native export facade did not transfer essentia");
                    require(strategy.push(key, 1, Actionable.SIMULATE) == 1, "Crafting push cannot see active bus");
                    upgrades.extractItem(accessSlot, 1, false);
                    require(strategy.push(key, 1, Actionable.MODULATE) == 0, "Crafting push bypassed access card");
                }
                level.setBlock(hostPos, Blocks.AIR.defaultBlockState(), 3);
            }
            var interfacePart = new InterfacePart(AEParts.INTERFACE.get());
            require(interfacePart.getUpgrades().getMaxInstalled(ModItems.ESSENTIA_ACCESS_CARD.get()) == 0,
                    "ME interface still accepts access card");
            require(appeng.api.upgrades.Upgrades.getMaxInstallable(ModItems.ESSENTIA_ACCESS_CARD.get(),
                    AEBlocks.INTERFACE.asItem()) == 0, "Block interface still accepts access card");
        } finally {
            level.setBlock(hostPos, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(target, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private static void persistenceChecks(MinecraftServer server, ImportBusPart original, IPartHost host,
            AEssentiaKey key) {
        original.getKeyTypeSelection().setEnabled(AEssentiaKeyType.INSTANCE, false);
        var recovery = ((EssentiaBusHost) original).tce$recoveryBuffer();
        recovery.store(key, 5);
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, server.registryAccess());
        original.writeToNBT(output);
        var reloaded = AEParts.IMPORT_BUS.get().createPart();
        reloaded.setPartHostInfo(Direction.DOWN, host, host.getBlockEntity());
        reloaded.readFromNBT(TagValueInput.create(ProblemReporter.DISCARDING,
                server.registryAccess(), output.buildResult()));
        require(!reloaded.getKeyTypeSelection().isEnabled(AEssentiaKeyType.INSTANCE),
                "Disabled resource type changed during load");
        require(reloaded.getUpgrades().insertItem(1, new ItemStack(AEItems.SPEED_CARD.get()), false).isEmpty(),
                "Reloaded native upgrade rejected card");
        require(!reloaded.getKeyTypeSelection().isEnabled(AEssentiaKeyType.INSTANCE),
                "Changing another upgrade after reload re-enabled essentia");
        require(((EssentiaBusHost) reloaded).tce$recoveryBuffer().held().amount() == 5,
                "Recovery contents were lost during NBT load");

        var drops = new ArrayList<ItemStack>();
        original.addPartDrop(drops, true);
        require(drops.size() == 1 && drops.getFirst().has(DataComponents.CUSTOM_DATA),
                "Dismantled bus lost recovery component");
        var replaced = AEParts.IMPORT_BUS.get().createPart();
        replaced.setPartHostInfo(Direction.DOWN, host, host.getBlockEntity());
        replaced.importSettings(SettingsFrom.DISMANTLE_ITEM, drops.getFirst().getComponents(), null);
        require(((EssentiaBusHost) replaced).tce$recoveryBuffer().held().amount() == 5,
                "Placed bus failed to restore recovery component");
        var memoryCopy = AEParts.IMPORT_BUS.get().createPart();
        memoryCopy.setPartHostInfo(Direction.DOWN, host, host.getBlockEntity());
        memoryCopy.importSettings(SettingsFrom.MEMORY_CARD, original.exportSettings(SettingsFrom.MEMORY_CARD), null);
        require(((EssentiaBusHost) memoryCopy).tce$recoveryBuffer().isEmpty(),
                "Memory card copied material recovery contents");
        original.getUpgrades().insertItem(2, new ItemStack(AEItems.REDSTONE_CARD.get()), false);
        original.getConfigManager().putSetting(appeng.api.config.Settings.REDSTONE_CONTROLLED,
                appeng.api.config.RedstoneMode.HIGH_SIGNAL);
        require(!original.getTickingRequest(null).isSleeping(), "Recovery fell asleep under redstone control");
        original.clearContent();
        require(recovery.isEmpty(), "Cleared old part retained already-dropped recovery contents");
        System.out.println("[essentia-bus-persistence] PASS: disabled selection survives reload and upgrade changes; recovery survives NBT and dismantling, memory cards do not copy it");
    }

    private static void nativeExportBudgetChecks(MinecraftServer server, UpgradeablePart original,
            IPartHost host, AEssentiaKey key) {
        var bus = new BudgetExportBus();
        bus.setPartHostInfo(Direction.DOWN, host, original.getBlockEntity());
        bus.getUpgrades().insertItem(5, new ItemStack(ModItems.ESSENTIA_ACCESS_CARD.get()), false);
        bus.getConfig().setStack(0, new appeng.api.stacks.GenericStack(key, 1));
        bus.getConfig().setStack(1, new appeng.api.stacks.GenericStack(key, 1));
        var network = new Network();
        network.amounts.put(key, 9L);
        var energy = new Energy(1000);
        var service = storageService(network);
        var energyService = gridEnergy(energy);
        IGrid grid = (IGrid) Proxy.newProxyInstance(EssentiaBusChecks.class.getClassLoader(),
                new Class<?>[] {IGrid.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getStorageService" -> service;
                    case "getEnergyService" -> energyService;
                    default -> null;
                });
        require(bus.evaluate(grid) && network.amount(key) == 0 && bus.visitedSlots == 1,
                "Native export bus did not charge two operations for nine points");
        require(energy.amount == 910, "Native bus transfer fee changed after isolating operation factor");
        System.out.println("[essentia-bus-budget] PASS: native export loop moves nine points in two operations, retains 10 AE per point, and stops before the next slot");
    }

    private static final class BudgetExportBus extends ExportBusPart {
        int visitedSlots;
        BudgetExportBus() { super(AEParts.EXPORT_BUS.get()); }
        @Override
        protected int getOperationsPerTick() { return 2; }
        @Override
        protected int getStartingSlot(SchedulingMode mode, int slot) {
            visitedSlots++;
            return super.getStartingSlot(mode, slot);
        }
        boolean evaluate(IGrid grid) { return super.doBusWork(grid); }
    }

    private static MEStorage transactionalNetwork(Tank tank) {
        var delegate = new EssentiaMEStorage(tank);
        return new MEStorage() {
            @Override
            public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
                return mode == Actionable.SIMULATE ? amount : delegate.insert(what, amount, mode, source);
            }
            @Override
            public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
                return mode == Actionable.SIMULATE ? amount : delegate.extract(what, amount, mode, source);
            }
            @Override
            public Component getDescription() { return Component.literal("transactional network fixture"); }
        };
    }

    private static StackTransferContext context(MEStorage network, IEnergySource energy, int operations,
            IPartitionList filter, IActionSource source) {
        var service = storageService(network);
        try {
            var type = Class.forName("appeng.parts.automation.StackTransferContextImpl");
            var ctor = type.getDeclaredConstructor(IStorageService.class, IEnergySource.class,
                    IActionSource.class, int.class, IPartitionList.class);
            ctor.setAccessible(true);
            return (StackTransferContext) ctor.newInstance(service, energy, source, operations, filter);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static IStorageService storageService(MEStorage network) {
        return (IStorageService) Proxy.newProxyInstance(EssentiaBusChecks.class.getClassLoader(),
                new Class<?>[] {IStorageService.class}, (proxy, method, args) ->
                        method.getName().equals("getInventory") ? network : null);
    }

    private static IEnergyService gridEnergy(Energy energy) {
        return (IEnergyService) Proxy.newProxyInstance(EssentiaBusChecks.class.getClassLoader(),
                new Class<?>[] {IEnergyService.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "extractAEPower" -> energy.extractAEPower((double) args[0], (Actionable) args[1],
                            (PowerMultiplier) args[2]);
                    case "injectPower" -> { energy.amount += (double) args[0]; yield 0.0; }
                    default -> null;
                });
    }

    private static final class Energy implements IEnergySource {
        double amount;
        double actualLimit = Double.MAX_VALUE;
        Energy(double amount) { this.amount = amount; }
        @Override
        public double extractAEPower(double wanted, Actionable mode, PowerMultiplier multiplier) {
            double extracted = Math.min(amount, Math.min(wanted, mode == Actionable.MODULATE ? actualLimit : wanted));
            if (mode == Actionable.MODULATE) { amount -= extracted; }
            return extracted;
        }
    }

    private static final class Network implements MEStorage {
        final Map<AEKey, Long> amounts = new HashMap<>();
        long limit = Long.MAX_VALUE;
        long actualInsertLimit = Long.MAX_VALUE;
        long actualExtractLimit = Long.MAX_VALUE;
        boolean failInsert;
        Runnable afterExtract = () -> {};
        long amount(AEKey key) { return amounts.getOrDefault(key, 0L); }
        @Override
        public long insert(AEKey key, long wanted, Actionable mode, IActionSource source) {
            long inserted = Math.min(wanted, Math.max(0, limit - amount(key)));
            if (mode == Actionable.MODULATE) {
                if (failInsert) { throw new IllegalStateException("expected network insertion failure"); }
                inserted = Math.min(inserted, actualInsertLimit);
                amounts.merge(key, inserted, Long::sum);
            }
            return inserted;
        }
        @Override
        public long extract(AEKey key, long wanted, Actionable mode, IActionSource source) {
            long taken = Math.min(wanted, amount(key));
            if (mode == Actionable.MODULATE) {
                taken = Math.min(taken, actualExtractLimit);
                amounts.put(key, amount(key) - taken);
                afterExtract.run();
            }
            return taken;
        }
        @Override
        public Component getDescription() { return Component.literal("bus regression network"); }
    }

    private static final class Tank extends SnapshotJournal<AspectList> implements IEssentiaStorage {
        final int capacity;
        AspectList contents;
        boolean acceptsInsert = true;
        int insertCalls;
        int secondInsertLimit = Integer.MAX_VALUE;
        boolean failSecondInsert;
        boolean failRootCommit;
        Tank(int capacity, AspectList contents) { this.capacity = capacity; this.contents = contents; }
        @Override
        public AspectList contents() { return contents; }
        @Override
        public int insert(Holder<IAspect> aspect, int wanted, TransactionContext transaction) {
            insertCalls++;
            if (insertCalls == 2 && failSecondInsert) {
                throw new IllegalStateException("expected target insertion failure");
            }
            if (!acceptsInsert) { return 0; }
            int accepted = Math.min(wanted, capacity - amount(aspect));
            if (insertCalls == 2) { accepted = Math.min(accepted, secondInsertLimit); }
            updateSnapshots(transaction);
            contents = contents.add(aspect, accepted);
            return accepted;
        }
        @Override
        public int extract(Holder<IAspect> aspect, int wanted, TransactionContext transaction) {
            int taken = Math.min(wanted, amount(aspect));
            updateSnapshots(transaction);
            contents = contents.remove(aspect, taken);
            return taken;
        }
        @Override
        public long contentRevision() { return 0; }
        @Override
        protected AspectList createSnapshot() { return contents; }
        @Override
        protected void revertToSnapshot(AspectList snapshot) { contents = snapshot; }
        @Override
        protected void onRootCommit(AspectList original) {
            if (failRootCommit) {
                failRootCommit = false;
                throw new IllegalStateException("expected post-commit callback failure");
            }
        }
    }

    private static void storageUpgradeMigration(MinecraftServer server, StorageBusPart bus) {
        for (boolean full : new boolean[] {false, true}) {
            bus.clearContent();
            var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, server.registryAccess());
            var upgrades = output.childrenList("upgrades");
            if (full) {
                for (int i = 0; i < 5; i++) {
                    var entry = upgrades.addChild();
                    entry.store(ItemStack.MAP_CODEC, new ItemStack(AEItems.SPEED_CARD.get()));
                    entry.putInt("Slot", i);
                }
            }
            var entry = upgrades.addChild();
            entry.store(ItemStack.MAP_CODEC, new ItemStack(ModItems.ESSENTIA_ACCESS_CARD.get()));
            entry.putInt("Slot", 5);
            bus.readFromNBT(TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(), output.buildResult()));
            if (!full) {
                require(EssentiaBusAccess.hasCard(bus) && bus.getUpgrades().size() == 5,
                        "Old sixth-slot card did not migrate into a free slot");
            } else {
                require(!EssentiaBusAccess.hasCard(bus), "Pending migration acted as a sixth upgrade slot");
                var saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, server.registryAccess());
                bus.writeToNBT(saved);
                require(saved.buildResult().contains("tceLegacyUpgrade"), "Full bus lost its old sixth-slot card");
                var drops = new ArrayList<ItemStack>();
                bus.addAdditionalDrops(drops, true);
                require(drops.stream().filter(s -> s.is(ModItems.ESSENTIA_ACCESS_CARD.get())).count() == 1,
                        "Dismantling did not return the pending card exactly once");
                bus.getUpgrades().extractItem(2, 1, false);
                require(EssentiaBusAccess.hasCard(bus) && bus.getUpgrades().size() == 5,
                        "Pending card did not migrate after freeing an upgrade slot");
            }
        }
        bus.clearContent();
        System.out.println("[storage-bus-slots] PASS: five slots, legacy card migrated or retained, one dismantle drop and migration on removal");
    }

    private static void require(boolean condition, String message) {
        if (!condition) { throw new IllegalStateException(message); }
    }
}
