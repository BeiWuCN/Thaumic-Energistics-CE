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
 * 带我们访问卡的每个 ME 接口，以及驱动它们的轮次。区块加载和菜单打开各自接纳宿主；
 * 扫描随后重访已加载区块，每 tick 一个。全部不存档：目录由世界里的实际内容重建，
 * 宿主消失的条目在下一个轮次被丢掉。
 */
public final class EssentiaInterfaceRegistry {

    private static final List<Entry> ENTRIES = new ArrayList<>();

    /**
     * 已加载的区块，按维度分组，由加载和卸载事件维护。
     * level 问不到区块列表，扫描只能遍历这里。
     */
    private static final Map<ResourceKey<Level>, Set<ChunkPos>> LOADED = new HashMap<>();

    /** 每 tick 一个区块，大世界里扫描也不会吃掉 tick 预算。 */
    private static int cursor;

    private EssentiaInterfaceRegistry() {}

    /** 把每条发现路径挂到游戏总线。mod 构造时调一次。 */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(EssentiaInterfaceRegistry::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(EssentiaInterfaceRegistry::onChunkUnload);
        NeoForge.EVENT_BUS.addListener(EssentiaInterfaceRegistry::onContainerOpen);
        NeoForge.EVENT_BUS.addListener(EssentiaInterfaceRegistry::onServerTick);
        NeoForge.EVENT_BUS.addListener(EssentiaInterfaceRegistry::onServerStopped);
    }

    /**
     * 区块来了就加入扫描，里面已有的接口当场接纳。存档加载后目录靠这条路径恢复。
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

    /** 区块离开就退出扫描。 */
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            forget(level, event.getChunk().getPos());
        }
    }

    /**
     * 玩家打开了接口的菜单：区块扫描漏掉的情形。
     * 接口可能一直在已加载的区块里，只是刚拿到卡。
     */
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (event.getContainer() instanceof InterfaceMenu menu
                && menu.getHost() instanceof InterfaceLogicHost host) {
            admit(host);
        }
    }

    /** 推进一格慢扫描，再跑轮次；轮次错开，任意两个不会落在同一个 tick。 */
    public static void onServerTick(ServerTickEvent.Post event) {
        sweep(event.getServer().getAllLevels());
        dispatchRounds(event.getServer().getTickCount());
    }

    /**
     * 服务端停止要清掉目录：条目指向的方块实体属于已经不存在的世界；
     * 区块列表按维度作键，下一个世界会继承上一个的。
     */
    public static void onServerStopped(ServerStoppedEvent event) {
        ENTRIES.clear();
        LOADED.clear();
        cursor = 0;
    }

    /**
     * 接纳还不认识的宿主。宿主除了对象本身没有身份，
     * 同一个接口从区块加载和菜单各被看到一次，最后只能算一个条目。
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
     * 给卡还装着的条目跑一轮，丢掉宿主已消失的。
     * 相位取条目在目录里的位置，五个 tick 轮到每个接口一次。
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

    /** 把位置加进所在维度的扫描。 */
    private static void remember(ServerLevel level, ChunkPos pos) {
        LOADED.computeIfAbsent(level.dimension(), key -> new HashSet<>()).add(pos);
    }

    /** 把位置从所在维度的扫描里移除。 */
    private static void forget(ServerLevel level, ChunkPos pos) {
        Set<ChunkPos> positions = LOADED.get(level.dimension());
        if (positions != null) {
            positions.remove(pos);
        }
    }

    /** 区块贡献的宿主，取的是副本：活方块实体列表会在遍历中被改动。 */
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

    /** 轮流推进每个维度的扫描，丢掉已经消失的区块。 */
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
            LevelChunk chunk = source.getChunkNow(pos.x(), pos.z());
            if (chunk == null) {
                // 任何状态都不存在：这个位置回不来了，扫描把它忘掉。
                if (!source.hasChunk(pos.x(), pos.z())) {
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

    /** 一个接口和绑定到它的轮次驱动器。 */
    private record Entry(InterfaceLogicHost host, EssentiaInterfaceAccess access) {}
}
