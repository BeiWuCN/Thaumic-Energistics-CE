package thaumicenergistics_ce.interfaceaccess;

import appeng.api.networking.IManagedGridNode;
import appeng.api.parts.IPartHost;
import appeng.blockentity.misc.InterfaceBlockEntity;
import appeng.helpers.InterfaceLogicHost;
import appeng.menu.implementations.InterfaceMenu;
import appeng.parts.misc.InterfacePart;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Every ME interface that carries our access card, plus the rounds that drive them. A chunk load and a
 * menu opening each admit a host; a sweep then revisits the loaded chunks, one per tick.
 * Nothing here is ever saved, since the catalog is rebuilt from what is in the world, and an entry
 * whose host is gone is dropped on the first round that notices it.
 */
public final class EssentiaInterfaceRegistry {

    private static final List<Entry> ENTRIES = new ArrayList<>();

    /**
     * The loaded chunks, per dimension, as told to us by the load and unload events. A level cannot be
     * asked for its chunks, so the sweep walks this instead.
     */
    private static final Map<ResourceKey<Level>, Set<ChunkPos>> LOADED = new HashMap<>();

    /** One chunk per tick, so the sweep cannot become the tick budget of a large world. */
    private static int cursor;

    private EssentiaInterfaceRegistry() {}

    /** Hooks every discovery path to the game bus. Called once, from the mod's own construction. */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(EssentiaInterfaceRegistry::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(EssentiaInterfaceRegistry::onChunkUnload);
        NeoForge.EVENT_BUS.addListener(EssentiaInterfaceRegistry::onContainerOpen);
        NeoForge.EVENT_BUS.addListener(EssentiaInterfaceRegistry::onServerTick);
        NeoForge.EVENT_BUS.addListener(EssentiaInterfaceRegistry::onServerStopped);
    }

    /**
     * A chunk that arrives: it joins the sweep, and an interface already inside it is admitted now.
     * This is what restores the catalog after a save is loaded.
     */
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        remember(level, event.getChunk().getPos());
        if (event.getChunk() instanceof LevelChunk chunk) {
            for (InterfaceLogicHost host : hostsIn(chunk)) {
                admit(host);
            }
        }
    }

    /** A chunk that goes away leaves the sweep: nothing that is unloaded is worth a visit. */
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            forget(level, event.getChunk().getPos());
        }
    }

    /**
     * A player opening an interface's menu: the case the chunk sweep misses, since the interface may have
     * been in a loaded chunk all along and only just received the card.
     */
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (event.getContainer() instanceof InterfaceMenu menu
                && menu.getHost() instanceof InterfaceLogicHost host) {
            admit(host);
        }
    }

    /** Steps the slow sweep, then runs the rounds, which are staggered so that no two share a tick. */
    public static void onServerTick(ServerTickEvent.Post event) {
        sweep(event.getServer().getAllLevels());
        dispatchRounds(event.getServer().getTickCount());
    }

    /**
     * A stopped server takes the catalog with it: the entries name block entities of a world that is gone,
     * and the chunk list is keyed by dimension, so the next world would inherit the previous one's.
     */
    public static void onServerStopped(ServerStoppedEvent event) {
        ENTRIES.clear();
        LOADED.clear();
        cursor = 0;
    }

    /**
     * Adds a host that is not known yet. A host has no identity beyond the object itself, so the same
     * interface seen twice - by a chunk load and then by a menu - must still end up as one entry.
     */
    private static void admit(InterfaceLogicHost host) {
        if (host.getBlockEntity() == null || host.getInterfaceLogic() == null) {
            return;
        }
        for (Entry entry : ENTRIES) {
            if (entry.host() == host) {
                return;
            }
        }
        IManagedGridNode node = nodeOf(host);
        if (node != null) {
            ENTRIES.add(new Entry(host, new EssentiaInterfaceAccess(host, node)));
        }
    }

    /**
     * Runs one round for every entry whose card is still installed, dropping those whose host is gone. The
     * phase is the entry's place in the catalog, so a round finds each interface five ticks on.
     */
    private static void dispatchRounds(long tick) {
        int now = Math.floorMod(tick, EssentiaInterfaceAccess.ROUND_TICKS);
        for (Entry entry : List.copyOf(ENTRIES)) {
            if (!entry.access().stillValid()) {
                ENTRIES.remove(entry);
                continue;
            }
            if (Math.floorMod(ENTRIES.indexOf(entry), EssentiaInterfaceAccess.ROUND_TICKS) == now) {
                entry.access().runRound();
            }
        }
    }

    /** Adds a position to the sweep of its dimension. */
    private static void remember(ServerLevel level, ChunkPos pos) {
        LOADED.computeIfAbsent(level.dimension(), key -> new HashSet<>()).add(pos);
    }

    /** Drops a position from the sweep of its dimension. */
    private static void forget(ServerLevel level, ChunkPos pos) {
        Set<ChunkPos> positions = LOADED.get(level.dimension());
        if (positions != null) {
            positions.remove(pos);
        }
    }

    /** The hosts a chunk contributes, taken as a copy: a live block entity list mutates under the walk. */
    private static List<InterfaceLogicHost> hostsIn(LevelChunk chunk) {
        List<InterfaceLogicHost> hosts = new ArrayList<>();
        if (chunk == null) {
            return hosts;
        }
        for (var blockEntity : List.copyOf(chunk.getBlockEntities().values())) {
            if (blockEntity instanceof InterfaceBlockEntity) {
                hosts.add((InterfaceLogicHost) blockEntity);
            }
            if (blockEntity instanceof IPartHost partHost) {
                for (Direction side : Direction.values()) {
                    if (partHost.getPart(side) instanceof InterfacePart part) {
                        hosts.add(part);
                    }
                }
            }
        }
        return hosts;
    }

    /** Advances the sweep by one chunk of every dimension in turn, dropping chunks that are gone. */
    private static void sweep(Iterable<ServerLevel> levels) {
        for (ServerLevel level : levels) {
            Set<ChunkPos> positions = LOADED.get(level.dimension());
            if (positions == null || positions.isEmpty()) {
                continue;
            }
            cursor = (cursor + 1) % positions.size();
            Iterator<ChunkPos> walk = positions.iterator();
            for (int skipped = 0; skipped < cursor; skipped++) {
                walk.next();
            }
            ChunkPos pos = walk.next();
            ServerChunkCache source = level.getChunkSource();
            LevelChunk chunk = source.getChunkNow(pos.x, pos.z);
            if (chunk == null) {
                // Absent at every status: the position cannot come back, so the sweep forgets it.
                if (!source.hasChunk(pos.x, pos.z)) {
                    walk.remove();
                }
                continue;
            }
            for (InterfaceLogicHost host : hostsIn(chunk)) {
                admit(host);
            }
        }
    }

    private static IManagedGridNode nodeOf(InterfaceLogicHost host) {
        if (host instanceof InterfaceBlockEntity block) {
            return block.getMainNode();
        }
        if (host instanceof InterfacePart part) {
            return part.getMainNode();
        }
        return null;
    }

    /** One interface and the round driver bound to it. */
    private record Entry(InterfaceLogicHost host, EssentiaInterfaceAccess access) {}
}
