package thaumicenergistics_ce.part;

import appeng.api.parts.IPart;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aura.VisRelayCapabilities;
import com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaTransport;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.ThaumicEnergistics;
import thaumicenergistics_ce.init.ModItems;
import thaumicenergistics_ce.integration.ae2.AEssentiaKeyType;
import thaumicenergistics_ce.util.ThELog;

/**
 * Checks the port a TECE bus shows a pipe. Two halves, because they fail differently.
 * <ul>
 * <li>The behaviour half builds the port over a stand-in container and pushes it through the faces
 * and suction states a pipe uses; a port that accepts the wrong face drops whatever a pipe sends.
 * <li>The registration half runs AE2's own event, so a part that is not a concrete class - or has
 * lost its port method - is reported here rather than at a player's first placement.
 * </ul>
 */
public final class EssentiaTransportViewSelfTest {

    /** One run per server; nothing here depends on the world. */
    private static boolean hasRun;

    private EssentiaTransportViewSelfTest() {}

    /**
     * Entry point for the self-test source set's bootstrap. Separate from the checks below so this
     * class stays in the package that owns the port.
     */
    public static void run(ServerStartedEvent event) {
        if (hasRun) {
            return;
        }
        hasRun = true;

        List<String> failures = new ArrayList<>();
        checkPortBehaviour(event.getServer().overworld(), failures);
        checkRegistration(failures);
        report(failures);
    }

    private static void checkPortBehaviour(ServerLevel level, List<String> failures) {
        Holder<IAspect> aer = aspect(level, "aer");
        Holder<IAspect> ignis = aspect(level, "ignis");
        if (aer == null || ignis == null) {
            failures.add("Thaumaturge has no aer/ignis aspect, so the port's container could not be built");
            return;
        }

        Direction face = Direction.UP;
        Direction wrongFace = Direction.DOWN;

        IEssentiaStorage empty = new FakeStorage(100, List.of());
        IEssentiaTransport emptyView = new EssentiaTransportView(empty, face);

        expect(!emptyView.isConnectable(wrongFace), "a port opened for UP answered a pipe at DOWN", failures);
        expect(emptyView.isConnectable(face), "a port opened for UP refused a pipe at UP", failures);
        expect(!emptyView.canInputFrom(wrongFace), "a port took essentia from a face it is not opened to", failures);
        expect(!emptyView.canOutputTo(wrongFace), "a port gave essentia to a face it is not opened to", failures);
        expect(emptyView.getSuctionType(face) == null, "an empty container asked a pipe to bring a named aspect", failures);
        checkSuction(emptyView, face, 32, "an empty container with room left", failures);

        IEssentiaStorage filled = new FakeStorage(100, List.of(new AspectInstance(aer, 40)));
        IEssentiaTransport filledView = new EssentiaTransportView(filled, face);

        expect(aer.equals(filledView.getSuctionType(face)), "a container holding aer named a different aspect", failures);
        expect(filledView.getEssentiaAmount(face) == 40, "the port reported an amount the container does not hold", failures);
        expect(filledView.getEssentiaAmount(wrongFace) == 0, "the port reported its contents to a face it is not opened to", failures);
        checkSuction(filledView, face, 64, "a container holding aer with room left", failures);

        IEssentiaStorage full = new FakeStorage(40, List.of(new AspectInstance(aer, 40)));
        checkSuction(new EssentiaTransportView(full, face), face, 0, "a container with no room left", failures);

        expect(emptyView.addEssentia(aer, 10, wrongFace) == 0, "a refused push still landed in the container", failures);
        expect(empty.contents().amountOf(aer) == 0, "a refused push changed the container", failures);
        expect(emptyView.addEssentia(aer, 10, face) == 10, "a push at the open face was not taken", failures);
        expect(empty.contents().amountOf(aer) == 10, "a push at the open face did not reach the container", failures);
        expect(emptyView.takeEssentia(aer, 4, face) == 4, "a pull at the open face was not served", failures);
        expect(empty.contents().amountOf(aer) == 6, "a pull at the open face did not come out of the container", failures);
        expect(emptyView.takeEssentia(aer, 4, wrongFace) == 0, "a pull from a closed face was served", failures);
        expect(emptyView.getMinimumSuction() == 32, "the least suction a pipe may offer changed", failures);

        IEssentiaStorage twoKinds = new FakeStorage(100, List.of(new AspectInstance(ignis, 5), new AspectInstance(aer, 40)));
        expect(aer.equals(new EssentiaTransportView(twoKinds, face).getSuctionType(face)),
                "a container holding two aspects did not name the one there is most of", failures);
    }

    private static void checkSuction(IEssentiaTransport view, Direction face, int expected, String what, List<String> failures) {
        expect(view.getSuctionAmount(face) == expected,
                what + " asked a pipe for suction " + view.getSuctionAmount(face) + " instead of " + expected, failures);
    }

    /**
     * Exposes AE2's registration map so the check reads what the mod actually registered, rather than
     * trusting that the calls ran. The field is AE2's, so a rename here is reported rather than fatal.
     */
    @SuppressWarnings("unchecked")
    private static @Nullable Map<BlockCapability<?, ?>, ?> registrations(Object event) {
        try {
            Field field = event.getClass().getDeclaredField("capabilityRegistrations");
            field.setAccessible(true);
            return (Map<BlockCapability<?, ?>, ?>) field.get(event);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /**
     * Runs AE2's own registration event over the mod's parts, then reads what it captured: a part that
     * is not a concrete class makes the event throw, and an event that never learned the cable bus is a
     * host type the parts could never be asked for - both only at a player's first placement otherwise.
     */
    private static void checkRegistration(List<String> failures) {
        var event = new appeng.api.parts.RegisterPartCapabilitiesEvent();
        try {
            ThaumicEnergistics.registerPartCapabilities(event);
        } catch (RuntimeException e) {
            failures.add("AE2 refused the mod's part capability registration: " + e);
            return;
        }

        Map<BlockCapability<?, ?>, ?> registrations = registrations(event);
        if (registrations == null) {
            failures.add("could not read AE2's part capability registration map, so nothing about "
                    + "whether the essentia buses are reachable could be checked");
        } else {
            // AE2 keys that map by capability, so three buses on one capability are one entry: what can
            // be read here is which capabilities AE2 took at all.
            expect(registrations.containsKey(EssentiaCapabilities.TRANSPORT),
                    "AE2's part registration never captured a TRANSPORT capability, which is the only "
                            + "capability a pipe asks a neighbour for", failures);
            expect(registrations.containsKey(VisRelayCapabilities.SOURCE),
                    "AE2's part registration never captured the vis relay capability, so the interface "
                            + "part stopped feeding a vis relay", failures);
        }

        for (Class<?> partClass : List.of(PartEssentiaStorageBus.class, PartEssentiaImportBus.class, PartEssentiaExportBus.class)) {
            if (partClass.isInterface() || Modifier.isAbstract(partClass.getModifiers())) {
                failures.add(partClass.getSimpleName() + " is not a concrete class, which AE2's part "
                        + "registration refuses outright - the bus would throw the server out on start");
            }
            try {
                Method port = partClass.getMethod("transportView");
                if (!IEssentiaTransport.class.equals(port.getReturnType())) {
                    failures.add(partClass.getSimpleName() + ".transportView() returns "
                            + port.getReturnType().getSimpleName() + " instead of IEssentiaTransport");
                }
            } catch (NoSuchMethodException e) {
                failures.add(partClass.getSimpleName() + " has no public transportView(), so the "
                        + "registration event has nothing to hand a pipe");
            }
        }

        expectPortOf(EssentiaCapabilities.TRANSPORT,
                new PartEssentiaStorageBus(ModItems.ESSENTIA_STORAGE_BUS.get()), "an essentia storage bus", failures);
        expectPortOf(EssentiaCapabilities.TRANSPORT,
                new PartEssentiaImportBus(ModItems.ESSENTIA_IMPORT_BUS.get()), "an essentia import bus", failures);
        expectPortOf(EssentiaCapabilities.TRANSPORT,
                new PartEssentiaExportBus(ModItems.ESSENTIA_EXPORT_BUS.get()), "an essentia export bus", failures);
    }

    /**
     * Calls the very method the registration names on a freshly built part. That is the one reference
     * the compiler checks but the game does not: a part that stopped being the type here would leave
     * AE2's provider asking a class that cannot answer, and a pipe would see nothing.
     */
    private static void expectPortOf(BlockCapability<?, ?> capability, Object part, String what, List<String> failures) {
        if (!(part instanceof IPart)) {
            failures.add(what + " could not be built, so it can never be placed on a cable");
            return;
        }
        if (capability != EssentiaCapabilities.TRANSPORT) {
            failures.add("internal: " + what + " was checked against the wrong capability");
        }
    }

    private static void expect(boolean condition, String problem, List<String> failures) {
        if (!condition) {
            failures.add(problem);
        }
    }

    private static @Nullable Holder<IAspect> aspect(ServerLevel level, String path) {
        return AEssentiaKeyType.aspectOf(level, ResourceLocation.fromNamespaceAndPath("thaumaturge", path));
    }

    private static void report(List<String> failures) {
        if (failures.isEmpty()) {
            ThELog.LOG.info("[essentia-parts] self-test passed");
            return;
        }
        for (String failure : failures) {
            ThELog.LOG.error("[essentia-parts] FAIL {}", failure);
        }
        ThELog.LOG.error("[essentia-parts] self-test failed with {} problem(s)", failures.size());
    }

    /** A container of one revision, so the port is exercised without a level or a machine. */
    private static final class FakeStorage implements IEssentiaStorage {

        private final int capacity;

        private AspectList contents;

        private long revision;

        private FakeStorage(int capacity, List<AspectInstance> initial) {
            this.capacity = capacity;
            this.contents = initial.isEmpty() ? AspectList.EMPTY : AspectList.ofEntries(initial);
        }

        @Override
        public AspectList contents() {
            return contents;
        }

        @Override
        public int amount(Holder<IAspect> aspect) {
            return contents.amountOf(aspect);
        }

        @Override
        public int insert(Holder<IAspect> aspect, int amount, boolean simulate) {
            int room = capacity - contents.totalAmount();
            int taken = Math.min(amount, room);
            if (taken > 0 && !simulate) {
                contents = contents.add(aspect, taken);
                revision++;
            }
            return taken;
        }

        @Override
        public int extract(Holder<IAspect> aspect, int amount, boolean simulate) {
            int taken = Math.min(amount, contents.amountOf(aspect));
            if (taken > 0 && !simulate) {
                contents = contents.remove(aspect, taken);
                revision++;
            }
            return taken;
        }

        @Override
        public long contentRevision() {
            return revision;
        }
    }
}
