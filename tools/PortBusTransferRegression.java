package thaumicenergistics_ce;

import appeng.api.behaviors.StackTransferContext;
import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import java.lang.reflect.Proxy;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import thaumicenergistics_ce.integration.ae2.EssentiaBusBuffer;
import thaumicenergistics_ce.integration.ae2.EssentiaBusTransfer;

@GameTestHolder(ThEIds.MODID)
@PrefixGameTestTemplate(false)
public final class PortBusTransferRegression {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void transferConservation(GameTestHelper helper) {
        var fixture = new Fixture();
        fixture.external = 17;
        fixture.networkCapacity = 6;
        fixture.energy = 100;
        require(EssentiaBusTransfer.importFrom(fixture.context(), fixture.storage(), fixture.buffer), "Import did not run");
        require(fixture.external == 11 && fixture.network == 6 && fixture.buffer.isEmpty() && fixture.energy == 40,
                "Partial import violated conservation");

        fixture = new Fixture();
        fixture.network = 20;
        fixture.commitLimit = 3;
        require(EssentiaBusTransfer.exportTo(fixture.context(), fixture.storage(), fixture.key, 10, fixture.buffer) == 3,
                "Partial export result");
        require(fixture.network == 17 && fixture.external == 3 && fixture.buffer.isEmpty() && fixture.energy == 970,
                "Partial export refund or conservation");

        fixture = new Fixture();
        fixture.network = 20;
        fixture.networkCapacity = 0;
        fixture.commitLimit = 3;
        fixture.throwAfterInsert = true;
        require(EssentiaBusTransfer.exportTo(fixture.context(), fixture.storage(), fixture.key, 10, fixture.buffer) == 3,
                "Failed external insert was not reconciled");
        require(fixture.network == 10 && fixture.external == 3 && fixture.buffer.held().amount() == 7,
                "Failed export lost or duplicated essentia");
        fixture.networkCapacity = 100;
        fixture.buffer.returnToNetwork(fixture.inventory(), IActionSource.empty(), Long.MAX_VALUE, fixture.power());
        require(fixture.network == 17 && fixture.buffer.isEmpty() && fixture.energy == 970, "Recovery did not refund energy");

        fixture = new Fixture();
        fixture.network = 20;
        fixture.energy = 0;
        require(EssentiaBusTransfer.exportTo(fixture.context(), fixture.storage(), fixture.key, 10, fixture.buffer) == 0
                && fixture.network == 20 && fixture.external == 0, "Unpowered export changed resources");
        System.out.println("PORT PASS import/export partial acceptance, failure recovery, energy refund and power loss");
        helper.succeed();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class Fixture {
        final AEssentiaKey key = AEssentiaKey.of(ResourceLocation.fromNamespaceAndPath("thaumaturge", "aer"));
        final EssentiaBusBuffer buffer = new EssentiaBusBuffer(() -> {});
        long network;
        int external;
        int networkCapacity = 100;
        int commitLimit = 100;
        boolean throwAfterInsert;
        double energy = 1000;
        int operations = 10;

        IEssentiaStorage storage() {
            return new IEssentiaStorage() {
                public AspectList contents() { return external == 0 ? AspectList.EMPTY : AspectList.EMPTY.add(key.resolveAspect(), external); }
                public int insert(Holder<IAspect> aspect, int amount, boolean simulate) {
                    int accepted = simulate ? amount : Math.min(amount, commitLimit);
                    if (!simulate) {
                        external += accepted;
                        if (throwAfterInsert) throw new IllegalStateException("Intentional partial-insert failure");
                    }
                    return accepted;
                }
                public int extract(Holder<IAspect> aspect, int amount, boolean simulate) {
                    int taken = Math.min(amount, external);
                    if (!simulate) external -= taken;
                    return taken;
                }
                public long contentRevision() { return external; }
            };
        }

        MEStorage inventory() {
            return new MEStorage() {
                public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
                    long inserted = Math.min(amount, Math.max(0, networkCapacity - network));
                    if (mode == Actionable.MODULATE) network += inserted;
                    return inserted;
                }
                public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
                    long taken = Math.min(amount, network);
                    if (mode == Actionable.MODULATE) network -= taken;
                    return taken;
                }
                public void getAvailableStacks(KeyCounter out) { out.add(key, network); }
                public net.minecraft.network.chat.Component getDescription() { return net.minecraft.network.chat.Component.literal("Fixture"); }
            };
        }

        IEnergyService power() {
            return (IEnergyService) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {IEnergyService.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "extractAEPower" -> {
                            double taken = Math.min((double) args[0], energy);
                            if (args[1] == Actionable.MODULATE) energy -= taken;
                            yield taken;
                        }
                        case "injectPower" -> { energy += (double) args[0]; yield 0.0; }
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }

        StackTransferContext context() {
            var service = (IStorageService) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {IStorageService.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("getInventory")) return inventory();
                        throw new UnsupportedOperationException(method.getName());
                    });
            return (StackTransferContext) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {StackTransferContext.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getInternalStorage" -> service;
                        case "getEnergySource" -> power();
                        case "getActionSource" -> IActionSource.empty();
                        case "getOperationsRemaining" -> operations;
                        case "hasOperationsLeft" -> operations > 0;
                        case "isKeyTypeEnabled", "isInFilter" -> true;
                        case "isInverted" -> false;
                        case "reduceOperationsRemaining" -> { operations -= ((Long) args[0]).intValue(); yield null; }
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }
    }
}
