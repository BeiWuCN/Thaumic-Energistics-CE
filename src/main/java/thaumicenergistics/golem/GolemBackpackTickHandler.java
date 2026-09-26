package thaumicenergistics.golem;

import com.leclowndu93150.thaumaturge.content.golem.EntityThaumaturgeGolem;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumicenergistics.ThEIds;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.network.GolemBackpackPayload;

/**
 * What a golem does with its backpack: tips what it is carrying into the ME network, and tells nearby
 * clients whether the network is still reachable.
 *
 * <p><b>The errand is the gate.</b> The reference build let the golem's core decide, and only gather cores
 * fed the network. Thaumaturge golems have no cores, so the equivalent question is the golem's
 * {@code Task}: a golem with one is delivering what it holds for a seal, a golem without one is holding
 * something nobody asked for. Without that check the backpack would quietly rob every seal that uses a
 * golem to carry things, which would read as the seals being broken.
 *
 * <p>The client cannot see any of this - the link lives in unsynced persistent data, and the skin has to be
 * drawn - so the golem's watchers are sent the skin and whether the network is reachable, on a slightly
 * different interval from the transfers so the two never settle onto the same tick.
 */
@EventBusSubscriber(modid = ThEIds.MODID)
public final class GolemBackpackTickHandler {

    /** Ticks between network operations. */
    private static final int NETWORK_COOLDOWN = 20;

    /** Ticks between status updates to the client. */
    private static final int SYNC_INTERVAL = 100;

    /** The last status and skin sent per golem, so the heartbeat only speaks when something changed. */
    private static final Map<UUID, int[]> LAST_SENT = Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * The two countdowns per golem, weakly keyed.
     *
     * <p>They used to live in the golem's saved data, which meant two NBT writes a tick per golem - a hash
     * put and a boxed int each - for a number nothing ever saves.
     */
    private static final Map<UUID, int[]> COOLDOWNS = Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * Ticks between two "running an errand" traces for the same golem.
     *
     * <p>That line is true every second for as long as an errand lasts, and a diagnostic that repeats itself
     * once a second buries the line that matters. Ten seconds is long enough to notice and short enough to
     * follow.
     */
    private static final long ERRAND_TRACE_INTERVAL = 200L;

    /** Golem UUID to the game time its errand was last reported, weakly held. */
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
                ThaumicEnergistics.LOG.info("[pack] golem " + golem.getId() + " cannot reach " + link
                        + ": " + GolemWirelessLink.refusal(golem, link));
            }
            return;
        }
        deposit(golem, connection);
    }

    /**
     * Moves what the golem is holding into the network, one stack per operation.
     *
     * <p>One stack, and not the whole load: a golem carries one stack, or two with the hauler addon, and
     * the cooldown is what limits the rate. Two stacks in one operation would double the rate for haulers
     * and make the rate table mean something different depending on the golem's addon.
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
            // Named before the transfer, not after: inserting shrinks the stack, and an empty stack answers
            // Items.AIR, so reading the item afterwards logged every successful transfer as "1x minecraft:air".
            // The name is only built when something will read it.
            String name = GolemBackpackHandler.TRACE ? itemName(carried) : null;
            long inserted = connection.insert(carried, rate);
            if (inserted > 0L) {
                if (name != null) {
                    ThaumicEnergistics.LOG.info("[pack] golem " + golem.getId() + " put " + inserted + "x "
                            + name + " into the network");
                }
                // The arm swing is the golem's own tell that it just did something, and it is what
                // Thaumaturge plays when a golem hands an item over.
                golem.swingArm();
                return;
            }
        }
    }

    /** The registry name of a stack, for the transfer line. Only ever called when tracing. */
    private static String itemName(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /** Reports a golem that is holding something for a seal, at most once every ten seconds each. */
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
        ThaumicEnergistics.LOG.info("[pack] golem " + golem.getId()
                + " is running an errand, so what it holds is not the network's");
    }

    private static void syncStatus(EntityThaumaturgeGolem golem, GlobalPos link) {
        if (cooling(golem, 1, SYNC_INTERVAL)) {
            return;
        }
        GolemBackpackPayload payload = payloadFor(golem, link);
        // Nothing to say unless something changed: the heartbeat exists for the player who starts watching
        // this golem, not to repeat the same three numbers a second at a time to everyone who already knows.
        // The player who *starts* watching is served by onStartTracking; without it, "nothing changed" also
        // covered "nobody has ever been told", and the skin was never sent to a client that had just loaded.
        int[] last = LAST_SENT.get(golem.getUUID());
        if (last != null && last[0] == payload.status() && last[1] == payload.skinOrdinal()) {
            return;
        }
        LAST_SENT.put(golem.getUUID(), new int[] {payload.status(), payload.skinOrdinal()});
        PacketDistributor.sendToPlayersTrackingEntity(golem, payload);
    }

    /** What a client should draw on this golem right now. */
    private static GolemBackpackPayload payloadFor(EntityThaumaturgeGolem golem, GlobalPos link) {
        int status = GolemWirelessLink.open(golem, link) != null
                ? GolemBackpackPayload.STATUS_IN_RANGE
                : GolemBackpackPayload.STATUS_OUT_OF_RANGE;
        int skin = GolemBackpackHandler.getSkin(golem).ordinal();
        return new GolemBackpackPayload(golem.getId(), status, skin);
    }

    /**
     * Tells a player who has just started watching a golem what is on its back.
     *
     * <p>The heartbeat below only speaks when something changes, and a player who has just logged in has never
     * been told anything: their client's copy of the skin died with the last connection. So the backpack stayed
     * on the golem and was not drawn - which is exactly what a lost backpack looks like
     * (*"重进存档，傀儡身上的无线傀儡背包会丢"*). The data was never lost: the golem's own save carries
     * {@code ThEWifiBackpackLink} and {@code ThEBackpackSkin}, and it did so all along.
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
            // Nothing to say: a golem nobody has put a backpack on is drawn as a golem.
            return;
        }
        PacketDistributor.sendToPlayer(player, payloadFor(golem, link));
    }

    /**
     * Counts a cooldown down in the golem's own data.
     *
     * @return true while the cooldown is still running, and false on the tick it expires - which is the
     *     tick the caller should do the work and start a new one.
     */
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
