package thaumicenergistics_ce.golem;

import com.leclowndu93150.thaumaturge.content.golem.EntityThaumaturgeGolem;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.network.GolemBackpackPayload;
import thaumicenergistics_ce.util.ThELog;

/**
 * 傀儡拿背包做什么：倒进 ME 网络，能碰到时告知附近的客户端。
 * 门槛是「有差事」：Thaumaturge 的傀儡没有核心，核心检查变成 {@code getTask()}；
 * 少了它，背包会把所有用傀儡的印记都抢走。
 * 客户端看不到链接（数据未同步），观察者按自己的间隔收到外观。
 */
@EventBusSubscriber(modid = ThEIds.MODID)
public final class GolemBackpackTickHandler {

    private static final int NETWORK_COOLDOWN = 20;

    private static final int SYNC_INTERVAL = 100;

    /** 每个傀儡上次发出的状态和外观，心跳只在变化时出声。 */
    private static final Map<UUID, int[]> LAST_SENT = Collections.synchronizedMap(new WeakHashMap<>());

    /** 每个傀儡的两个倒计时，弱引用键。不放进存档数据：那会每 tick 付两次 NBT 写入。 */
    private static final Map<UUID, int[]> COOLDOWNS = Collections.synchronizedMap(new WeakHashMap<>());

    private static final long ERRAND_TRACE_INTERVAL = 200L;

    private static final Map<UUID, Long> LAST_ERRAND_TRACE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private GolemBackpackTickHandler() {}

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof EntityThaumaturgeGolem golem)) {
            return;
        }
        if (!(golem.level() instanceof ServerLevel)) {
            return;
        }
        if (!GolemBackpackHandler.hasBackpack(golem)) {
            return;
        }
        GlobalPos link = GolemBackpackHandler.getLink(golem);
        if (link == null) {
            return;
        }

        syncStatus(golem, link);

        if (cooling(golem, 0, NETWORK_COOLDOWN)) {
            return;
        }
        GolemWirelessLink connection = GolemWirelessLink.open(golem, link);
        if (connection == null) {
            if (GolemBackpackHandler.TRACE) {
                ThELog.LOG.info("[pack] golem " + golem.getId() + " cannot reach " + link
                        + ": " + GolemWirelessLink.refusal(golem, link));
            }
            return;
        }
        deposit(golem, connection);
    }

    /**
     * 把傀儡拿的东西搬进网络，一次一个物品堆：速度由冷却决定。
     * 一次搬两个会让搬运傀儡的速度翻倍，速率表也就随附加 mod 变。
     */
    private static void deposit(EntityThaumaturgeGolem golem, GolemWirelessLink connection) {
        if (golem.getTask() != null) {
            traceErrand(golem);
            return;
        }
        int rate = GolemWirelessLink.itemRate(golem);
        for (ItemStack carried : golem.getCarrying()) {
            if (carried.isEmpty()) {
                continue;
            }
            // 先取名字再插入：插入之后物品堆被清空，名字会变成 "minecraft:air"。
            String name = GolemBackpackHandler.TRACE ? itemName(carried) : null;
            long inserted = connection.insert(carried, rate);
            if (inserted > 0L) {
                if (name != null) {
                    ThELog.LOG.info("[pack] golem " + golem.getId() + " put " + inserted + "x "
                            + name + " into the network");
                }
                // Thaumaturge 傀儡递出物品时放的挥手动作。
                golem.swingArm();
                return;
            }
        }
    }

    private static String itemName(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static void traceErrand(EntityThaumaturgeGolem golem) {
        if (!GolemBackpackHandler.TRACE || golem.getCarrying().isEmpty()) {
            return;
        }
        long now = golem.level().getGameTime();
        Long last = LAST_ERRAND_TRACE.get(golem.getUUID());
        if (last != null && now - last < ERRAND_TRACE_INTERVAL) {
            return;
        }
        LAST_ERRAND_TRACE.put(golem.getUUID(), now);
        ThELog.LOG.info("[pack] golem " + golem.getId()
                + " is running an errand, so what it holds is not the network's");
    }

    private static void syncStatus(EntityThaumaturgeGolem golem, GlobalPos link) {
        if (cooling(golem, 1, SYNC_INTERVAL)) {
            return;
        }
        GolemBackpackPayload payload = payloadFor(golem, link);
        // 只在变化时发；刚来观察的玩家由 [onStartTracking] 负责，不在这里。
        int[] last = LAST_SENT.get(golem.getUUID());
        if (last != null && last[0] == payload.status() && last[1] == payload.skinOrdinal()) {
            return;
        }
        LAST_SENT.put(golem.getUUID(), new int[] {payload.status(), payload.skinOrdinal()});
        PacketDistributor.sendToPlayersTrackingEntity(golem, payload);
    }

    private static GolemBackpackPayload payloadFor(EntityThaumaturgeGolem golem, GlobalPos link) {
        int status = GolemWirelessLink.open(golem, link) != null
                ? GolemBackpackPayload.STATUS_IN_RANGE
                : GolemBackpackPayload.STATUS_OUT_OF_RANGE;
        int skin = GolemBackpackHandler.getSkin(golem).ordinal();
        return new GolemBackpackPayload(golem.getId(), status, skin);
    }

    /**
     * 告诉刚来看某个傀儡的玩家它背上有什么：心跳只在变化时出声，
     * 刚登录的客户端收不到，也就画不出背包。
     */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getTarget() instanceof EntityThaumaturgeGolem golem)) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        GlobalPos link = GolemBackpackHandler.getLink(golem);
        if (link == null) {
            // 没什么可说的：没装背包的傀儡按普通傀儡画。
            return;
        }
        PacketDistributor.sendToPlayer(player, payloadFor(golem, link));
    }

    private static boolean cooling(EntityThaumaturgeGolem golem, int slot, int cooldown) {
        int[] counts = COOLDOWNS.computeIfAbsent(golem.getUUID(), key -> new int[2]);
        if (counts[slot] > 0) {
            counts[slot]--;
            return true;
        }
        counts[slot] = cooldown;
        return false;
    }
}
