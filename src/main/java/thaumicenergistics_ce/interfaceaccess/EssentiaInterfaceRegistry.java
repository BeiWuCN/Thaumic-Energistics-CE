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
 * 每一个带我们访问卡的 ME 接口，以及驱动它们的轮次。
 * 区块加载和菜单打开各自接纳一个宿主；然后一次扫描会重访已加载的区块，每 tick 一个。
 * 这里的东西从不存档，因为目录是由世界里的实际内容重建的；
 * 宿主已消失的条目会在第一个注意到它的轮次里被丢弃。
 */
public final class EssentiaInterfaceRegistry {

    private static final List<Entry> ENTRIES = new ArrayList<>();

    /**
     * 已加载的区块，按维度分组，由加载与卸载事件告知。无法向 level 索取它的区块列表，
     * 所以扫描改为遍历这里。
     */
    private static final Map<ResourceKey<Level>, Set<ChunkPos>> LOADED = new HashMap<>();

    /** 每 tick 一个区块，这样扫描不会变成大世界的 tick 预算。 */
    private static int cursor;

    private EssentiaInterfaceRegistry() {}

    /** 把每条发现路径都挂到游戏总线上。只调用一次，在 mod 自身构造时。 */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(EssentiaInterfaceRegistry::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(EssentiaInterfaceRegistry::onChunkUnload);
        NeoForge.EVENT_BUS.addListener(EssentiaInterfaceRegistry::onContainerOpen);
        NeoForge.EVENT_BUS.addListener(EssentiaInterfaceRegistry::onServerTick);
        NeoForge.EVENT_BUS.addListener(EssentiaInterfaceRegistry::onServerStopped);
    }

    /**
     * 一个区块到来：它加入扫描，其中已有的接口立即被接纳。
     * 这就是存档加载后恢复目录的机制。
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

    /** 区块离开就退出扫描：卸载了的东西不值得再访问。 */
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            forget(level, event.getChunk().getPos());
        }
    }

    /**
     * 玩家打开了某个接口的菜单：这是区块扫描漏掉的情况，因为接口可能一直在已加载的区块里，
     * 只是刚刚才拿到卡。
     */
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (event.getContainer() instanceof InterfaceMenu menu
                && menu.getHost() instanceof InterfaceLogicHost host) {
            admit(host);
        }
    }

    /** 推进一步缓慢扫描，然后运行轮次；轮次是错开的，所以任意两个不会共用同一个 tick。 */
    public static void onServerTick(ServerTickEvent.Post event) {
        sweep(event.getServer().getAllLevels());
        dispatchRounds(event.getServer().getTickCount());
    }

    /**
     * 服务端停止会把目录一起带走：条目指向的方块实体属于一个已经不存在的世界，
     * 而区块列表按维度作键，下一个世界会继承上一个世界的。
     */
    public static void onServerStopped(ServerStoppedEvent event) {
        ENTRIES.clear();
        LOADED.clear();
        cursor = 0;
    }

    /**
     * 加入一个尚不知晓的宿主。宿主除了对象本身没有身份，所以同一个接口被看到两次——
     * 一次来自区块加载，一次来自菜单——也必须最终只算一个条目。
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
     * 为每个卡仍安装着的条目跑一轮，并丢弃宿主已消失的那些。相位是条目在目录中的位置，
     * 所以一轮每五个 tick 找到每个接口一次。
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

    /** 把一个位置加入它所在维度的扫描。 */
    private static void remember(ServerLevel level, ChunkPos pos) {
        LOADED.computeIfAbsent(level.dimension(), key -> new HashSet<>()).add(pos);
    }

    /** 把一个位置从它所在维度的扫描中移除。 */
    private static void forget(ServerLevel level, ChunkPos pos) {
        Set<ChunkPos> positions = LOADED.get(level.dimension());
        if (positions != null) {
            positions.remove(pos);
        }
    }

    /** 一个区块贡献的宿主，取的是副本：活着的方块实体列表会在遍历中被改动。 */
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

    /** 轮流把每个维度的扫描推进一步，并丢弃已消失的区块。 */
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
                // 所有状态下都不存在：这个位置不可能回来了，所以扫描忘掉它。
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

    /** 一个接口以及绑定到它的轮次驱动器。 */
    private record Entry(InterfaceLogicHost host, EssentiaInterfaceAccess access) {}
}
