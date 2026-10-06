package thaumicenergistics_ce.selftest;

import io.netty.buffer.Unpooled;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Supplier;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.network.ArcaneCraftCostPayload;
import thaumicenergistics_ce.network.ArcaneUnbindPayload;
import thaumicenergistics_ce.network.EncoderActionPayload;
import thaumicenergistics_ce.network.EncoderSourcePayload;
import thaumicenergistics_ce.network.EssentiaBusConfigPayload;
import thaumicenergistics_ce.network.EssentiaDepositPayload;
import thaumicenergistics_ce.network.EssentiaFillPayload;
import thaumicenergistics_ce.network.EssentiaInterfaceMarkPayload;
import thaumicenergistics_ce.network.GolemBackpackPayload;
import thaumicenergistics_ce.network.InscriberGridFillPayload;
import thaumicenergistics_ce.network.InscriberGridPayload;
import thaumicenergistics_ce.network.PartitionWellPayload;
import thaumicenergistics_ce.util.ThELog;

/**
 * Asserts the wire contract of every payload: a codec that lost a field round trips into a record that
 * lies, and a package move cannot be seen by a compiler. Each codec must survive a write and a read with
 * nothing left in the buffer - ItemStack has no equals, so stacks go through ItemStack.matches - and the
 * twelve agreed wire ids must all be the ones NeoForge holds for this mod. Runs only behind its own env var.
 */
public final class NetworkSelfTest {

    private static final Set<ResourceLocation> REGISTERED = readRegistrations(ThEIds.MODID);

    private static boolean hasRun;

    private NetworkSelfTest() {}

    public static void run(ServerStartedEvent event) {
        if (!"true".equalsIgnoreCase(System.getenv("THAUMICENERGISTICS_NETWORK_SELFTEST"))) {
            return;
        }
        if (hasRun) {
            return;
        }
        hasRun = true;

        RegistryAccess registries = event.getServer().registryAccess();
        List<String> failures = new ArrayList<>();
        if (REGISTERED == null) {
            failures.add("NeoForge registered no payload at all by the time this ran");
        }
        List<ResourceLocation> admitted = new ArrayList<>(REGISTERED);
        List<ResourceLocation> required = checkWireIds(admitted, failures);
        checkRegistrations(admitted, required, failures);
        checkCodecs(registries, failures);
        report(failures);
    }

    /**
     * Read reflectively on purpose: no accessor exists, so a hand-kept list compared against itself would
     * prove nothing. The table is global, so it is filtered to one namespace.
     */
    private static Set<ResourceLocation> readRegistrations(String namespace) {
        try {
            Field field = Class.forName("net.neoforged.neoforge.network.registration.NetworkRegistry")
                    .getDeclaredField("PAYLOAD_REGISTRATIONS");
            field.setAccessible(true);
            Map<?, ?> byProtocol = (Map<?, ?>) field.get(null);
            Set<ResourceLocation> found = new LinkedHashSet<>();
            for (Object perFlow : byProtocol.values()) {
                for (Object registration : ((Map<?, ?>) perFlow).values()) {
                    Field type = registration.getClass().getDeclaredField("type");
                    type.setAccessible(true);
                    ResourceLocation id = ((CustomPacketPayload.Type<?>) type.get(registration)).id();
                    if (namespace.equals(id.getNamespace())) {
                        found.add(id);
                    }
                }
            }
            return found;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static final List<ResourceLocation> WIRE_IDS = List.of(
            ThEIds.id("inscriber_grid"),
            ThEIds.id("inscriber_grid_fill"),
            ThEIds.id("essentia_terminal_fill"),
            ThEIds.id("essentia_terminal_deposit"),
            ThEIds.id("essentia_bus_config"),
            ThEIds.id("essentia_interface_mark"),
            ThEIds.id("partition_well"),
            ThEIds.id("encoder_source"),
            ThEIds.id("encoder_action"),
            ThEIds.id("arcane_craft_cost"),
            ThEIds.id("golem_backpack"),
            ThEIds.id("arcane_terminal_unbind"));

    /** A payload built with the wrong namespace is not a compile error, only a connection error later. */
    private static List<ResourceLocation> checkWireIds(List<ResourceLocation> admitted, List<String> failures) {
        if (admitted.isEmpty()) {
            failures.add("no payload carried a wire id");
            return List.of();
        }
        Map<String, ResourceLocation> declared = new LinkedHashMap<>();
        for (CustomPacketPayload.Type<?> type : List.of(
                InscriberGridPayload.TYPE,
                InscriberGridFillPayload.TYPE,
                EssentiaFillPayload.TYPE,
                EssentiaDepositPayload.TYPE,
                EssentiaBusConfigPayload.TYPE,
                EssentiaInterfaceMarkPayload.TYPE,
                PartitionWellPayload.TYPE,
                EncoderSourcePayload.TYPE,
                EncoderActionPayload.TYPE,
                ArcaneCraftCostPayload.TYPE,
                ArcaneUnbindPayload.TYPE,
                GolemBackpackPayload.TYPE)) {
            declared.put(type.id().getPath(), type.id());
        }
        declared.forEach((path, id) -> {
            if (!ThEIds.MODID.equals(id.getNamespace())) {
                failures.add("payload " + path + " is namespaced " + id.getNamespace());
            }
        });
        List<ResourceLocation> required = new ArrayList<>();
        for (ResourceLocation id : WIRE_IDS) {
            required.add(id);
            ResourceLocation held = declared.get(id.getPath());
            if (held == null) {
                failures.add("no payload claims the wire id " + id);
            } else if (!held.equals(id)) {
                failures.add("the wire id " + id + " is declared as " + held);
            }
        }
        if (declared.size() != WIRE_IDS.size()) {
            failures.add("payloads declare " + declared.size() + " wire ids, expected " + WIRE_IDS.size());
        }
        return required;
    }

    /**
     * A payload that is written but never registered compiles and is simply never sent: the two lists have
     * to be compared with each other, and neither side is derivable from the other.
     */
    private static void checkRegistrations(List<ResourceLocation> admitted, List<ResourceLocation> required,
            List<String> failures) {
        for (ResourceLocation id : required) {
            if (!admitted.contains(id)) {
                failures.add("NeoForge did not register " + id);
            }
        }
        for (ResourceLocation id : admitted) {
            if (!required.contains(id)) {
                failures.add("NeoForge registered " + id + ", which this mod does not declare");
            }
        }
    }

    private static void checkCodecs(RegistryAccess registries, List<String> failures) {
        ItemStack stack = new ItemStack(Items.STONE, 3);
        ResourceLocation aspect = ThEIds.id("ignis");
        // A name no registry holds: the codec must carry it without resolving it, which is exactly what the
        // client does with the aspect ids the server sends.
        ResourceLocation unknown = ThEIds.id("network_selftest_aspect");

        List<Roundtrip<?>> cases = List.of(
                new Roundtrip<>(
                        "InscriberGridPayload",
                        () -> new InscriberGridPayload(7, 3, stack),
                        InscriberGridPayload.CODEC,
                        (wrote, read) -> wrote.containerId() == read.containerId()
                                && wrote.containerSlot() == read.containerSlot()
                                && sameStack(wrote.stack(), read.stack())),
                new Roundtrip<>(
                        "InscriberGridFillPayload",
                        () -> new InscriberGridFillPayload(7, List.of(stack)),
                        InscriberGridFillPayload.CODEC,
                        (wrote, read) -> wrote.containerId() == read.containerId() && sameStacks(wrote.cells(), read.cells())),
                new Roundtrip<>(
                        "EssentiaFillPayload",
                        () -> new EssentiaFillPayload(7, unknown, 2, stack, true),
                        EssentiaFillPayload.CODEC,
                        (wrote, read) -> wrote.containerId() == read.containerId() && wrote.aspectId().equals(read.aspectId())
                                && wrote.where() == read.where() && wrote.wholeStack() == read.wholeStack()
                                && sameStack(wrote.stack(), read.stack())),
                new Roundtrip<>(
                        "EssentiaDepositPayload",
                        () -> new EssentiaDepositPayload(7, 2, stack),
                        EssentiaDepositPayload.CODEC,
                        (wrote, read) -> wrote.containerId() == read.containerId() && wrote.where() == read.where()
                                && sameStack(wrote.stack(), read.stack())),
                new Roundtrip<>(
                        "EssentiaBusConfigPayload",
                        () -> new EssentiaBusConfigPayload(7, 1, unknown),
                        EssentiaBusConfigPayload.CODEC,
                        (wrote, read) -> wrote.containerId() == read.containerId() && wrote.configSlot() == read.configSlot()
                                && wrote.aspectId().equals(read.aspectId())),
                // The clear marker is a value of the same field, and a codec that dropped it would only ever
                // configure a slot, never clear one.
                new Roundtrip<>(
                        "EssentiaBusConfigPayload (clear)",
                        () -> new EssentiaBusConfigPayload(7, 1, EssentiaBusConfigPayload.CLEAR),
                        EssentiaBusConfigPayload.CODEC,
                        (wrote, read) -> wrote.containerId() == read.containerId() && wrote.configSlot() == read.configSlot()
                                && wrote.aspectId().equals(read.aspectId())),
                new Roundtrip<>(
                        "PartitionWellPayload",
                        () -> new PartitionWellPayload(7, 1, unknown),
                        PartitionWellPayload.CODEC,
                        (wrote, read) -> wrote.containerId() == read.containerId() && wrote.well() == read.well()
                                && wrote.aspectId().equals(read.aspectId())),
                new Roundtrip<>(
                        "EncoderSourcePayload",
                        () -> new EncoderSourcePayload(7, stack),
                        EncoderSourcePayload.CODEC,
                        (wrote, read) -> wrote.containerId() == read.containerId() && sameStack(wrote.stack(), read.stack())),
                new Roundtrip<>(
                        "ArcaneCraftCostPayload",
                        () -> new ArcaneCraftCostPayload(7, List.of(
                                new ArcaneCraftCostPayload.AspectCost(aspect, 250),
                                new ArcaneCraftCostPayload.AspectCost(unknown, 1))),
                        ArcaneCraftCostPayload.CODEC,
                        (wrote, read) -> wrote.containerId() == read.containerId() && wrote.aspects().equals(read.aspects())),
                new Roundtrip<>(
                        "ArcaneCraftCostPayload (empty)",
                        () -> ArcaneCraftCostPayload.none(7),
                        ArcaneCraftCostPayload.CODEC,
                        (wrote, read) -> wrote.containerId() == read.containerId() && wrote.aspects().equals(read.aspects())),
                new Roundtrip<>(
                        "GolemBackpackPayload",
                        () -> new GolemBackpackPayload(12345, GolemBackpackPayload.STATUS_IN_RANGE, 2),
                        GolemBackpackPayload.CODEC,
                        (wrote, read) -> Objects.equals(wrote, read)),
                new Roundtrip<>(
                        "GolemBackpackPayload (no backpack)",
                        () -> new GolemBackpackPayload(12345, GolemBackpackPayload.STATUS_NO_BACKPACK, 0),
                        GolemBackpackPayload.CODEC,
                        (wrote, read) -> Objects.equals(wrote, read)));

        for (Roundtrip<?> roundtrip : cases) {
            check(registries, failures, roundtrip);
        }
        // Every action the switch in the handler knows about, or a packet that reaches the server with an
        // action nobody decodes is a silent no-op.
        for (int action = 0; action < 3; action++) {
            int fixed = action;
            check(registries, failures, new Roundtrip<>(
                    "EncoderActionPayload action " + action,
                    () -> new EncoderActionPayload(7, fixed, 4),
                    EncoderActionPayload.CODEC,
                    (wrote, read) -> wrote.containerId() == read.containerId() && wrote.action() == read.action()
                            && wrote.value() == read.value()));
        }
    }

    /** One codec round trip and the field comparison that decides whether it survived. */
    private record Roundtrip<T extends CustomPacketPayload>(
            String what,
            Supplier<T> build,
            StreamCodec<RegistryFriendlyByteBuf, T> codec,
            BiPredicate<T, T> match) {}

    private static <T extends CustomPacketPayload> void check(RegistryAccess registries, List<String> failures,
            Roundtrip<T> roundtrip) {
        roundtrip(registries, failures, roundtrip.what(), roundtrip.build(), roundtrip.codec(), roundtrip.match());
    }

    private static boolean sameStack(ItemStack first, ItemStack second) {
        return ItemStack.matches(first, second) && first.getCount() == second.getCount();
    }

    private static boolean sameStacks(List<ItemStack> first, List<ItemStack> second) {
        if (first.size() != second.size()) {
            return false;
        }
        for (int i = 0; i < first.size(); i++) {
            if (!sameStack(first.get(i), second.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static <T extends CustomPacketPayload> void roundtrip(
            RegistryAccess registries,
            List<String> failures,
            String what,
            Supplier<T> build,
            StreamCodec<RegistryFriendlyByteBuf, T> codec,
            BiPredicate<T, T> match) {
        T payload = build.get();
        try {
            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
            codec.encode(buffer, payload);
            int written = buffer.readableBytes();
            if (written == 0) {
                failures.add(what + " wrote nothing");
                return;
            }
            T read = codec.decode(buffer);
            int left = buffer.readableBytes();
            if (left != 0) {
                failures.add(what + " left " + left + " byte(s) unread of " + written);
            }
            if (!match.test(payload, read)) {
                failures.add(what + " came back as " + read + " instead of " + payload);
            }
        } catch (RuntimeException e) {
            failures.add(what + " threw " + e);
        }
    }

    private static void report(List<String> failures) {
        if (failures.isEmpty()) {
            ThELog.LOG.info("[network] self-test passed: {} wire ids registered, codecs round tripped", WIRE_IDS.size());
            return;
        }
        for (String failure : failures) {
            ThELog.LOG.error("[network] FAIL {}", failure);
        }
        ThELog.LOG.error("[network] self-test failed with {} problem(s)", failures.size());
    }
}
