package thaumicenergistics_ce.interfaceaccess;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.util.ConfigInventory;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import thaumicenergistics_ce.util.ThELog;

/**
 * The two config rows of an interface that carries our access card, read and written as wholes: the
 * config row says which aspects may come in, the storage row is what the grid takes back when the card
 * comes out. Apart from the round, so a row can be reasoned about without a host in hand.
 */
public final class EssentiaInterfaceRows {

    private EssentiaInterfaceRows() {}

    /** The config row reduced to its essentia keys; an empty answer stands for "no filter at all". */
    static List<AEKey> whitelist(ConfigInventory row) {
        List<AEKey> listed = new ArrayList<>();
        for (int slot = 0; slot < row.size(); slot++) {
            AEKey key = row.getKey(slot);
            if (key instanceof AEssentiaKey) {
                listed.add(key);
            }
        }
        return listed;
    }

    /**
     * Whether the config row lets a key in. An empty row is no filter at all: entries that are not
     * essentia never count, so a row holding only items or fluids behaves like an empty one.
     */
    public static boolean mayEnter(List<AEKey> allowed, AEKey key) {
        boolean filtered = false;
        for (AEKey entry : allowed) {
            if (!(entry instanceof AEssentiaKey)) {
                continue;
            }
            filtered = true;
            if (entry.equals(key)) {
                return true;
            }
        }
        return !filtered;
    }

    /** Drops aspects an earlier build of this card let JEI write into the storage row. */
    static void dropStaleAspects(ConfigInventory storage) {
        for (int slot = 0; slot < storage.size(); slot++) {
            if (storage.getKey(slot) instanceof AEssentiaKey) {
                ThELog.LOG.info("[essentia-interface] clearing a stale aspect in storage slot {}", slot);
                storage.setStack(slot, null);
            }
        }
    }

    /**
     * Empties both rows of an interface whose card has just come out: the marks go, and the storage row
     * is handed back to the grid. An aspect the grid refuses is dropped; a key of another type stays.
     */
    public static void releaseRows(
            ConfigInventory config,
            ConfigInventory storage,
            @Nullable MEStorage network,
            IActionSource source) {
        config.clear();
        for (int slot = 0; slot < storage.size(); slot++) {
            GenericStack held = storage.getStack(slot);
            if (held == null) {
                continue;
            }
            long rest = held.amount() - returnToNetwork(held, network, source);
            if (rest <= 0) {
                storage.setStack(slot, null);
            } else if (held.what() instanceof AEssentiaKey) {
                ThELog.LOG.info(
                        "[essentia-interface] discarding {} of {} as the card comes out", rest, held.what());
                storage.setStack(slot, null);
            } else {
                storage.setStack(slot, new GenericStack(held.what(), rest));
            }
        }
    }

    /**
     * Clears the aspects out of a storage row that is about to be dropped, so that breaking an interface
     * never puts one on the ground. An aspect has no item to be dropped as; the rest goes to the grid.
     */
    public static void rescueEssentia(
            ConfigInventory storage,
            @Nullable MEStorage network,
            IActionSource source) {
        for (int slot = 0; slot < storage.size(); slot++) {
            GenericStack held = storage.getStack(slot);
            if (held == null || !(held.what() instanceof AEssentiaKey)) {
                continue;
            }
            long rest = held.amount() - returnToNetwork(held, network, source);
            if (rest > 0) {
                ThELog.LOG.info(
                        "[essentia-interface] discarding {} of {} as the interface goes", rest, held.what());
            }
            storage.setStack(slot, null);
        }
    }

    /** Whether a row holds an aspect, which is how an interface of ours is told from a plain one. */
    public static boolean holdsEssentia(ConfigInventory row) {
        for (int slot = 0; slot < row.size(); slot++) {
            if (row.getKey(slot) instanceof AEssentiaKey) {
                return true;
            }
        }
        return false;
    }

    /** What the grid takes of a held stack; a grid that is gone or full takes nothing. */
    private static long returnToNetwork(
            GenericStack held,
            @Nullable MEStorage network,
            IActionSource source) {
        if (network == null) {
            return 0;
        }
        return network.insert(held.what(), held.amount(), Actionable.MODULATE, source);
    }
}
