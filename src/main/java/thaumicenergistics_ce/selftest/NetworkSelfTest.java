package thaumicenergistics_ce.selftest;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import thaumicenergistics_ce.net.ArcaneCraftCostPayload;
import thaumicenergistics_ce.net.EncoderActionPayload;
import thaumicenergistics_ce.net.EncoderSourcePayload;
import thaumicenergistics_ce.net.EssentiaBusConfigPayload;
import thaumicenergistics_ce.net.EssentiaDepositPayload;
import thaumicenergistics_ce.net.EssentiaFillPayload;
import thaumicenergistics_ce.net.GolemBackpackPayload;
import thaumicenergistics_ce.net.InscriberGridFillPayload;
import thaumicenergistics_ce.net.InscriberGridPayload;
import thaumicenergistics_ce.net.PartitionWellPayload;
import thaumicenergistics_ce.util.ThELog;

/**
 * Asserts the wire contract of every payload, because moving a payload between packages cannot be seen by
 * a compiler: a codec that lost a field round trips into a record that lies. Each codec must survive a
 * write and a read with nothing left in the buffer, and carry the same fields back - ItemStack has no
 * equals, so stacks are compared with ItemStack.matches. Each wire id must still be the namespaced one, and
 * the protocol package must not name a menu or a screen. Runs only behind its own env var.
 */
public final class NetworkSelfTest {

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
        checkWireIds(failures);
        checkCodecs(registries, failures);
        report(failures);
    }

    private static void checkWireIds(List<String> failures) {
        Map<String, ResourceLocation> expected = new LinkedHashMap<>();
        for (CustomPacketPayload.Type<?> type : List.of(
                InscriberGridPayload.TYPE,
                InscriberGridFillPayload.TYPE,
                EssentiaFillPayload.TYPE,
                EssentiaDepositPayload.TYPE,
                EssentiaBusConfigPayload.TYPE,
                EncoderSourcePayload.TYPE,
                EncoderActionPayload.TYPE,
                ArcaneCraftCostPayload.TYPE,
                GolemBackpackPayload.TYPE,
                PartitionWellPayload.TYPE)) {
            expected.put(type.id().getPath(), type.id());
        }
        expected.forEach((path, id) -> {
            if (!ThEIds.MODID.equals(id.getNamespace())) {
                failures.add("payload " + path + " is namespaced " + id.getNamespace());
            }
        });
        List<String> wanted = List.of(
                "inscriber_grid",
                "inscriber_grid_fill",
                "essentia_terminal_fill",
                "essentia_terminal_deposit",
                "essentia_bus_config",
                "encoder_source",
                "encoder_action",
                "arcane_craft_cost",
                "golem_backpack",
                "partition_well");
        for (String path : wanted) {
            if (!expected.containsKey(path)) {
                failures.add("no payload claims the wire id " + path);
            }
        }
        if (expected.size() != wanted.size()) {
            failures.add("payloads claim " + expected.size() + " wire ids, expected " + wanted.size());
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
                        () -> new EssentiaFillPayload(7, unknown, 2, stack),
                        EssentiaFillPayload.CODEC,
                        (wrote, read) -> wrote.containerId() == read.containerId() && wrote.aspectId().equals(read.aspectId())
                                && wrote.where() == read.where() && sameStack(wrote.stack(), read.stack())),
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
            ThELog.LOG.info("[network] self-test passed");
            return;
        }
        for (String failure : failures) {
            ThELog.LOG.error("[network] FAIL {}", failure);
        }
        ThELog.LOG.error("[network] self-test failed with {} problem(s)", failures.size());
    }
}
