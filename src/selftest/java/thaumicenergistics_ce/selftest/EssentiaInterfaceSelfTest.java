package thaumicenergistics_ce.selftest;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.helpers.InterfaceLogic;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import thaumicenergistics_ce.interfaceaccess.EssentiaInterfaceAccess;
import thaumicenergistics_ce.network.EssentiaInterfaceMarkPayload;
import thaumicenergistics_ce.util.ThELog;

/**
 * Self-test for the ME interface's access card: the config row's whitelist rule and the round's rates.
 * <ul>
 *   <li>Off unless {@code THAUMICENERGISTICS_INTERFACE_SELFTEST=true}; runs on {@code ServerStartedEvent}.
 *   <li>Pure logic only: a headless gate has no player, no live grid and no JEI to drag anything in.
 *   <li>The AE2 side of the rule is a mixin, so one of the checks is only that it landed on the class.
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
        checkClearPayload(event, failures);
        if (failures.isEmpty()) {
            ThELog.LOG.info(
                    "[{}] passed: an empty config row pulls every aspect and a filled one only its own,"
                            + " {} points move every {} ticks at {} AE each, and AE2's plan is off",
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
        if (!hasMergedHandler("tce$refuseEssentiaInRow") || !hasMergedHandler("tce$dropEssentiaPlan")) {
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
}
