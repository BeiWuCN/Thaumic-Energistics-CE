package thaumicenergistics_ce.integration.thaumaturge.golem;

import thaumicenergistics_ce.config.ThEConfig;

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
import thaumicenergistics_ce.compat.thaumaturge.TcGolem;
import thaumicenergistics_ce.network.GolemBackpackPayload;
import thaumicenergistics_ce.util.ThELog;

@EventBusSubscriber(modid = ThEIds.MODID)
public final class GolemBackpackTickHandler {

    private static final int SYNC_INTERVAL = 100;

    private static final Map<UUID, int[]> LAST_SENT = Collections.synchronizedMap(new WeakHashMap<>());

    private static final Map<UUID, int[]> COOLDOWNS = Collections.synchronizedMap(new WeakHashMap<>());

    private static final long ERRAND_TRACE_INTERVAL = 200L;

    private static final Map<UUID, Long> LAST_ERRAND_TRACE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private GolemBackpackTickHandler() {}

    public static void clearBackpackState(EntityThaumaturgeGolem golem) {
        LAST_SENT.remove(golem.getUUID());
        COOLDOWNS.remove(golem.getUUID());
        LAST_ERRAND_TRACE.remove(golem.getUUID());
        if (!golem.level().isClientSide()) {
            PacketDistributor.sendToPlayersTrackingEntity(golem,
                    new GolemBackpackPayload(golem.getId(), GolemBackpackPayload.STATUS_NO_BACKPACK, 0));
        }
    }

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

        if (!ThEConfig.GOLEM_TRANSFER.get()) {
            return;
        }

        if (cooling(golem, 0, ThEConfig.GOLEM_COOLDOWN.get())) {
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

    private static void deposit(EntityThaumaturgeGolem golem, GolemWirelessLink connection) {
        if (TcGolem.hasActiveJob(golem)) {
            traceErrand(golem);
            return;
        }
        int rate = GolemWirelessLink.itemRate(golem);
        for (ItemStack carried : golem.hands().contents()) {
            if (carried.isEmpty()) {
                continue;
            }
            String name = GolemBackpackHandler.TRACE ? itemName(carried) : null;
            long inserted = connection.insert(carried, rate);
            if (inserted > 0L) {
                if (name != null) {
                    ThELog.LOG.info("[pack] golem " + golem.getId() + " put " + inserted + "x "
                            + name + " into the network");
                }
                golem.swingArm();
                return;
            }
        }
    }

    private static String itemName(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static void traceErrand(EntityThaumaturgeGolem golem) {
        if (!GolemBackpackHandler.TRACE || golem.hands().contents().isEmpty()) {
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
        int[] last = LAST_SENT.get(golem.getUUID());
        if (last != null && last[0] == payload.status() && last[1] == payload.skinOrdinal()) {
            return;
        }
        LAST_SENT.put(golem.getUUID(), new int[] {payload.status(), payload.skinOrdinal()});
        PacketDistributor.sendToPlayersTrackingEntity(golem, payload);
    }

    public static void syncNow(EntityThaumaturgeGolem golem) {
        if (golem.level().isClientSide()) {
            return;
        }
        GlobalPos link = GolemBackpackHandler.getLink(golem);
        if (link == null) {
            return;
        }
        var payload = payloadFor(golem, link);
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
