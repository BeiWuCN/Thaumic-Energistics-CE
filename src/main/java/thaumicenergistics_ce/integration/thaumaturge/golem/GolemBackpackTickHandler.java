package thaumicenergistics_ce.integration.thaumaturge.golem;

import thaumicenergistics_ce.config.ThEConfig;

import com.leclowndu93150.thaumaturge.content.golem.EntityThaumaturgeGolem;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.GlobalPos;
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

@EventBusSubscriber(modid = ThEIds.MODID)
public final class GolemBackpackTickHandler {

    private static final int SYNC_INTERVAL = 100;
    private static final int WORKING_TICKS = 8;
    private static final Map<UUID, Long> WORKING_UNTIL = Collections.synchronizedMap(new WeakHashMap<>());

    private static final Map<UUID, int[]> LAST_SENT = Collections.synchronizedMap(new WeakHashMap<>());

    private static final Map<UUID, int[]> COOLDOWNS = Collections.synchronizedMap(new WeakHashMap<>());

    private GolemBackpackTickHandler() {}

    public static void clearBackpackState(EntityThaumaturgeGolem golem) {
        LAST_SENT.remove(golem.getUUID());
        COOLDOWNS.remove(golem.getUUID());
        WORKING_UNTIL.remove(golem.getUUID());
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


        if (cooling(golem, 0, ThEConfig.GOLEM_COOLDOWN.get())) {
            return;
        }
        GolemWirelessLink connection = GolemWirelessLink.open(golem, link);
        if (connection == null) {

            return;
        }
        deposit(golem, connection);
    }

    private static void deposit(EntityThaumaturgeGolem golem, GolemWirelessLink connection) {
        if (TcGolem.hasActiveJob(golem)) {
            return;
        }
        int rate = GolemWirelessLink.itemRate(golem);
        for (ItemStack carried : golem.hands().contents()) {
            if (carried.isEmpty()) {
                continue;
            }
            long inserted = connection.insert(carried, rate);
            if (inserted > 0L) {

                golem.animateSwing();
                WORKING_UNTIL.put(golem.getUUID(), golem.level().getGameTime() + WORKING_TICKS);
                sendStatus(golem, new GolemBackpackPayload(golem.getId(), GolemBackpackPayload.STATUS_WORKING,
                        GolemBackpackHandler.getSkin(golem).ordinal()));
                return;
            }
        }
    }

    private static void syncStatus(EntityThaumaturgeGolem golem, GlobalPos link) {
        Long until = WORKING_UNTIL.get(golem.getUUID());
        if (until != null && golem.level().getGameTime() >= until) {
            WORKING_UNTIL.remove(golem.getUUID());
            sendStatus(golem, payloadFor(golem, link));
        }
        if (cooling(golem, 1, SYNC_INTERVAL)) {
            return;
        }
        sendStatus(golem, payloadFor(golem, link));
    }

    private static void sendStatus(EntityThaumaturgeGolem golem, GolemBackpackPayload payload) {
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
        boolean working = WORKING_UNTIL.getOrDefault(golem.getUUID(), 0L) > golem.level().getGameTime();
        int status = GolemWirelessLink.open(golem, link) != null
                ? working ? GolemBackpackPayload.STATUS_WORKING : GolemBackpackPayload.STATUS_IN_RANGE
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
