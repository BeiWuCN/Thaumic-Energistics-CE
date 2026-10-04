package thaumicenergistics_ce.golem;

import appeng.api.ids.AEComponents;
import com.leclowndu93150.thaumaturge.content.golem.EntityThaumaturgeGolem;
import com.leclowndu93150.thaumaturge.content.golem.ItemGolemBell;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.item.ItemGolemWirelessBackpack;
import thaumicenergistics_ce.util.ThELog;

/**
 * Putting the wireless backpack on a golem, taking it off again, and repainting it.
 * <ul>
 *   <li>Equip with a linked backpack, remove with a sneaking golem bell, repaint with a mapped block.
 *   <li>Not an accessory: those use a fixed five-id atlas and a {@code final} item with no AE2 link.
 *   <li>The link lives in the golem's persistent data, unsynced: {@link GolemBackpackTickHandler} pushes it.
 * </ul>
 */
@EventBusSubscriber(modid = ThEIds.MODID)
public final class GolemBackpackHandler {

    /** Where the linked network lives. The same shape AE2 stores on the item. */
    static final String KEY_LINK = "ThEWifiBackpackLink";
    static final String KEY_SKIN = "ThEBackpackSkin";
    static final String KEY_FACADE = "ThEBackpackFacade";

    /** Golem UUID to decoded link: the tick handler must not re-parse the same NBT twenty times a second. */
    private static final Map<UUID, GlobalPos> LINK_CACHE = Collections.synchronizedMap(new WeakHashMap<>());

    /** Set {@code THAUMICENERGISTICS_BACKPACK_TRACE} to log each equip, removal, repaint and transfer. */
    static final boolean TRACE = System.getenv("THAUMICENERGISTICS_BACKPACK_TRACE") != null;

    private GolemBackpackHandler() {}

    private static void trace(String message) {
        if (TRACE) {
            ThELog.LOG.info("[pack] " + message);
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof EntityThaumaturgeGolem golem)) {
            return;
        }

        Player player = event.getEntity();
        ItemStack held = player.getItemInHand(event.getHand());
        if (held.isEmpty()) {
            return;
        }

        if (held.getItem() instanceof ItemGolemWirelessBackpack backpack) {
            // Client only to swing: the server is where anything happens, and vanilla sends the interaction
            // packet before this event either way, so cancelling locally does not hide the click from it.
            if (event.getLevel().isClientSide()) {
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
                return;
            }
            if (equip(golem, player, held, backpack)) {
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
            }
            return;
        }

        if (held.getItem() instanceof ItemGolemBell) {
            // Sneak, because the bell alone is Thaumaturge's follow toggle; cancelling is what stops the
            // golem being pocketed along with its backpack.
            if (!player.isShiftKeyDown()) {
                return;
            }
            if (event.getLevel().isClientSide()) {
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
                return;
            }
            if (dismantle(golem, player)) {
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
            }
            return;
        }

        if (!hasBackpack(golem)) {
            return;
        }
        BackpackSkins skin = FacadeToSkinMapping.skinFor(held);
        if (skin == null) {
            return;
        }
        if (event.getLevel().isClientSide()) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            return;
        }
        if (repaint(golem, player, held, skin)) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    private static boolean equip(EntityThaumaturgeGolem golem, Player player, ItemStack held,
            ItemGolemWirelessBackpack backpack) {
        if (!owns(golem, player) || hasBackpack(golem)) {
            return false;
        }
        GlobalPos link = backpack.getLinkedPosition(held);
        if (link == null) {
            // Refused: a backpack with no network is a decoration, and the golem would never reach anything.
            return false;
        }

        setLink(golem, link);
        setSkin(golem, BackpackSkins.Thaumium);
        trace("equipped golem " + golem.getId() + " with link " + link);
        playEquipSound(golem);
        if (!player.isCreative()) {
            held.shrink(1);
        }
        return true;
    }

    private static boolean dismantle(EntityThaumaturgeGolem golem, Player player) {
        if (!owns(golem, player)) {
            return false;
        }
        GlobalPos link = getLink(golem);
        if (link == null) {
            return false;
        }

        ItemStack backpack = new ItemStack(thaumicenergistics_ce.init.ModItems.GOLEM_WIFI_BACKPACK.get());
        backpack.set(AEComponents.WIRELESS_LINK_TARGET, link);
        golem.spawnAtLocation(backpack);

        // The block goes back too: it was a real item the player spent, and repainting must not consume it.
        ItemStack facade = getFacade(golem);
        if (!facade.isEmpty() && !player.isCreative()) {
            golem.spawnAtLocation(facade);
        }

        clear(golem);
        trace("removed the backpack from golem " + golem.getId() + ", link " + link + " returned");
        playEquipSound(golem);
        return true;
    }

    private static boolean repaint(EntityThaumaturgeGolem golem, Player player, ItemStack held,
            BackpackSkins skin) {
        if (!owns(golem, player)) {
            return false;
        }

        ItemStack previous = getFacade(golem);
        if (!previous.isEmpty() && !player.isCreative()) {
            golem.spawnAtLocation(previous);
        }

        ItemStack facade = held.copyWithCount(1);
        setFacade(golem, facade);
        setSkin(golem, skin);

        if (!player.isCreative()) {
            held.shrink(1);
        }
        trace("repainted golem " + golem.getId() + " as " + skin);
        playEquipSound(golem);
        return true;
    }

    private static boolean owns(EntityThaumaturgeGolem golem, Player player) {
        return golem.ownerIdentity().map(uuid -> uuid.equals(player.getUUID())).orElse(false);
    }

    /**
     * The reference build's sound, the one a player already associates with putting something on a golem.
     * Thaumaturge has its own clack for accessories; this is a backpack, not one.
     */
    private static void playEquipSound(EntityThaumaturgeGolem golem) {
        golem.level().playSound(null, golem.getX(), golem.getY(), golem.getZ(),
                SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.NEUTRAL, 0.5F, 1.0F);
    }


    public static boolean hasBackpack(EntityThaumaturgeGolem golem) {
        return golem.getPersistentData().contains(KEY_LINK);
    }

    @Nullable
    public static GlobalPos getLink(EntityThaumaturgeGolem golem) {
        CompoundTag data = golem.getPersistentData();
        if (!data.contains(KEY_LINK)) {
            LINK_CACHE.remove(golem.getUUID());
            return null;
        }
        GlobalPos cached = LINK_CACHE.get(golem.getUUID());
        if (cached != null) {
            return cached;
        }
        GlobalPos decoded = GlobalPos.CODEC.parse(NbtOps.INSTANCE, data.getCompound(KEY_LINK)).result().orElse(null);
        if (decoded != null) {
            LINK_CACHE.put(golem.getUUID(), decoded);
        }
        return decoded;
    }

    public static void setLink(EntityThaumaturgeGolem golem, GlobalPos link) {
        GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, link)
                .result()
                .ifPresent(encoded -> golem.getPersistentData().put(KEY_LINK, encoded));
        LINK_CACHE.put(golem.getUUID(), link);
    }

    public static BackpackSkins getSkin(EntityThaumaturgeGolem golem) {
        return BackpackSkins.fromOrdinal(golem.getPersistentData().getInt(KEY_SKIN));
    }

    public static void setSkin(EntityThaumaturgeGolem golem, BackpackSkins skin) {
        golem.getPersistentData().putInt(KEY_SKIN, skin.ordinal());
    }

    public static ItemStack getFacade(EntityThaumaturgeGolem golem) {
        CompoundTag data = golem.getPersistentData();
        if (!data.contains(KEY_FACADE)) {
            return ItemStack.EMPTY;
        }
        return ItemStack.parse(golem.level().registryAccess(), data.getCompound(KEY_FACADE))
                .orElse(ItemStack.EMPTY);
    }

    private static void setFacade(EntityThaumaturgeGolem golem, ItemStack facade) {
        golem.getPersistentData().put(KEY_FACADE, (CompoundTag) facade.save(golem.level().registryAccess()));
    }

    public static void clear(EntityThaumaturgeGolem golem) {
        CompoundTag data = golem.getPersistentData();
        data.remove(KEY_LINK);
        data.remove(KEY_SKIN);
        data.remove(KEY_FACADE);
        LINK_CACHE.remove(golem.getUUID());
    }
}
