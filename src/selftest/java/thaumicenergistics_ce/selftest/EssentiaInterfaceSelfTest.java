package thaumicenergistics_ce.selftest;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.helpers.InterfaceLogic;
import appeng.util.ConfigInventory;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import thaumicenergistics_ce.interfaceaccess.EssentiaInterfaceAccess;
import thaumicenergistics_ce.network.EssentiaInterfaceMarkPayload;
import thaumicenergistics_ce.util.ThELog;

/**
 * Self-test for the ME interface's access card: the config row's whitelist rule, the round's rates, and
 * the rows on the way out.
 * <ul>
 *   <li>Off unless {@code THAUMICENERGISTICS_INTERFACE_SELFTEST=true}; runs on {@code ServerStartedEvent}.
 *   <li>Pure logic only: a headless gate has no player, no live grid and no JEI to drag anything in.
 *   <li>The AE2 side of the rules is a mixin, so one check is only that its handlers landed on the class.
 * </ul>
 */
public final class EssentiaInterfaceSelfTest {

    private static final String TAG = "essentia-interface";

    private EssentiaInterfaceSelfTest() {}

    public static void run(ServerStartedEvent event) {
        if (!"true".equalsIgnoreCase(System.getenv("THAUMICENERGISTICS_INTERFACE_SELFTEST"))) {
            return;
        }
        List<String> failures = new ArrayList<>();
        checkMixin(failures);
        checkRates(failures);
        checkWhitelist(failures);
        checkRows(failures);
        checkClearPayload(event, failures);
        if (failures.isEmpty()) {
            ThELog.LOG.info(
                    "[{}] passed: an empty config row pulls every aspect and a filled one only its own,"
                            + " {} points move every {} ticks at {} AE each, AE2's plan is off, and the"
                            + " rows go when the card does",
                    TAG,
                    EssentiaInterfaceAccess.POINTS_PER_ROUND,
                    EssentiaInterfaceAccess.ROUND_TICKS,
                    EssentiaInterfaceAccess.AE_PER_POINT);
            return;
        }
        for (String failure : failures) {
            ThELog.LOG.error("[{}] FAIL {}", TAG, failure);
        }
    }

    /** Without the mixin AE2 pulls the marked aspect out of the grid and hands out whatever its row holds. */
    private static void checkMixin(List<String> failures) {
        // Mixin renames a handler on its way into the target class, so the mixin's own name is a fragment.
        if (!hasMergedHandler("tce$refuseEssentiaInRow")
                || !hasMergedHandler("tce$dropEssentiaPlan")
                || !hasMergedHandler("tce$releaseRowsWithoutCard")
                || !hasMergedHandler("tce$rescueEssentiaFromDrops")) {
            failures.add("the interface mixin did not apply to AE2's interface logic");
        }
    }

    /** True when a handler with this name, however mixin mangled it, is a method of AE2's own class. */
    private static boolean hasMergedHandler(String name) {
        for (Method method : InterfaceLogic.class.getDeclaredMethods()) {
            if (method.getName().contains(name)) {
                return true;
            }
        }
        return false;
    }

    /** The three figures the round is priced and paced by, which no other check would notice drifting. */
    private static void checkRates(List<String> failures) {
        if (EssentiaInterfaceAccess.ROUND_TICKS != 5) {
            failures.add("ROUND_TICKS is " + EssentiaInterfaceAccess.ROUND_TICKS + ", not 5");
        }
        if (EssentiaInterfaceAccess.POINTS_PER_ROUND != 8) {
            failures.add("POINTS_PER_ROUND is " + EssentiaInterfaceAccess.POINTS_PER_ROUND + ", not 8");
        }
        if (EssentiaInterfaceAccess.AE_PER_POINT != 10.0) {
            failures.add("AE_PER_POINT is " + EssentiaInterfaceAccess.AE_PER_POINT + ", not 10.0");
        }
    }

    /** The whitelist rule, asked of the same method the round calls, on the keys a row can hold. */
    private static void checkWhitelist(List<String> failures) {
        // Declared as AEKey so the lists below are List<AEKey>, which is what the row hands over.
        AEKey rune = AEssentiaKey.of(ThEIds.id("selftest_rune"));
        AEKey other = AEssentiaKey.of(ThEIds.id("selftest_other"));
        // An item key stands for everything in a row that is not ours.
        AEKey stone = AEItemKey.of(Items.STONE);
        if (!EssentiaInterfaceAccess.mayEnter(List.of(), rune)) {
            failures.add("an empty config row refused an aspect");
        }
        if (!EssentiaInterfaceAccess.mayEnter(List.of(rune), rune)) {
            failures.add("a row listing an aspect refused that same aspect");
        }
        if (EssentiaInterfaceAccess.mayEnter(List.of(rune), other)) {
            failures.add("a row listing one aspect let a different one in");
        }
        if (!EssentiaInterfaceAccess.mayEnter(List.of(stone), rune)) {
            failures.add("a row holding only an item stopped acting as no filter");
        }
    }

    /**
     * Both rows on the way out, against a stand-in grid: a card coming out empties them, and breaking an
     * interface never leaves an aspect behind for AE2 to turn into a drop. A grid that takes nothing still
     * leaves nothing of ours in a row; a key of another type is not ours to throw away.
     */
    private static void checkRows(List<String> failures) {
        AEKey rune = AEssentiaKey.of(ThEIds.id("selftest_rune"));
        AEKey stone = AEItemKey.of(Items.STONE);
        ConfigInventory config = ConfigInventory.configStacks(9).build();
        ConfigInventory storage = ConfigInventory.storage(9).build();
        config.setStack(0, new GenericStack(rune, 1));
        storage.setStack(0, new GenericStack(rune, 4));
        storage.setStack(1, new GenericStack(stone, 2));
        TestStorage grid = new TestStorage(64);
        EssentiaInterfaceAccess.releaseRows(config, storage, grid, IActionSource.empty());
        if (config.getStack(0) != null) {
            failures.add("the card coming out left a mark in the config row");
        }
        if (storage.getStack(0) != null) {
            failures.add("the card coming out left an aspect in the storage row");
        }
        if (storage.getStack(1) != null) {
            failures.add("the card coming out left a key behind that the grid had room for");
        }
        if (grid.taken != 6) {
            failures.add("the card coming out sent " + grid.taken + " of 6 points to the grid");
        }
        // A grid with no room left: the aspect is discarded there, and the other key stays for the player.
        ConfigInventory strapped = ConfigInventory.storage(9).build();
        strapped.setStack(0, new GenericStack(rune, 3));
        strapped.setStack(1, new GenericStack(stone, 1));
        TestStorage full = new TestStorage(0);
        EssentiaInterfaceAccess.releaseRows(config, strapped, full, IActionSource.empty());
        if (strapped.getStack(0) != null) {
            failures.add("a full grid left an aspect where an uncarded interface could hand it out");
        }
        if (strapped.getStack(1) == null) {
            failures.add("a full grid cost a key that was not ours");
        }
        if (full.taken != 0) {
            failures.add("a grid with no room was credited with " + full.taken + " points");
        }
        // Broken rather than uninstalled: an aspect leaves the row, and a key of another type stays.
        ConfigInventory broken = ConfigInventory.storage(9).build();
        broken.setStack(0, new GenericStack(rune, 3));
        broken.setStack(1, new GenericStack(stone, 1));
        TestStorage half = new TestStorage(1);
        EssentiaInterfaceAccess.rescueEssentia(broken, half, IActionSource.empty());
        if (broken.getStack(0) != null) {
            failures.add("breaking an interface left an aspect in the row, to be dropped");
        }
        if (broken.getStack(1) == null) {
            failures.add("breaking an interface lost a key that was not ours");
        }
        if (half.taken != 1) {
            failures.add("a grid with room for one point was credited with " + half.taken);
        }
        // No grid at all: there is nowhere to send it, and it still must not stay in the row.
        ConfigInventory offline = ConfigInventory.storage(9).build();
        offline.setStack(0, new GenericStack(rune, 5));
        EssentiaInterfaceAccess.rescueEssentia(offline, null, IActionSource.empty());
        if (offline.getStack(0) != null) {
            failures.add("breaking an interface with no grid left an aspect to be dropped");
        }
    }

    /** The clearing mark, out and back: a field lost here is a slot a player cannot empty from JEI. */
    private static void checkClearPayload(ServerStartedEvent event, List<String> failures) {
        var sent = new EssentiaInterfaceMarkPayload(7, 3, EssentiaInterfaceMarkPayload.CLEAR);
        try {
            RegistryFriendlyByteBuf buffer =
                    new RegistryFriendlyByteBuf(Unpooled.buffer(), event.getServer().registryAccess());
            EssentiaInterfaceMarkPayload.CODEC.encode(buffer, sent);
            EssentiaInterfaceMarkPayload read = EssentiaInterfaceMarkPayload.CODEC.decode(buffer);
            if (!sent.equals(read)) {
                failures.add("the clearing mark came back as " + read + " instead of " + sent);
            }
            if (buffer.readableBytes() != 0) {
                failures.add("the mark left " + buffer.readableBytes() + " byte(s) unread");
            }
        } catch (RuntimeException e) {
            failures.add("the clearing mark threw " + e);
        }
    }

    /** A stand-in for the grid: it takes what its room allows, and counts what it took. */
    private static final class TestStorage implements MEStorage {

        private final long room;
        private long taken;

        TestStorage(long room) {
            this.room = room;
        }

        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            long moved = Math.min(Math.max(0, room - taken), amount);
            taken += moved;
            return moved;
        }

        @Override
        public Component getDescription() {
            return Component.literal("selftest");
        }
    }
}
