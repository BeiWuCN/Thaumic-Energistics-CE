package thaumicenergistics.golem;

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
import thaumicenergistics.ThEIds;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.item.ItemGolemWirelessBackpack;

/**
 * Putting the wireless backpack on a golem, taking it off again, and repainting it.
 *
 * <p>Three clicks, all of them on the golem:
 *
 * <ul>
 *   <li>a <em>linked</em> backpack in hand equips it, keeping the link;
 *   <li>a golem bell while sneaking takes it off and hands the backpack back with its link intact;
 *   <li>a block in hand repaints it, one skin per material - see {@link FacadeToSkinMapping}.
 * </ul>
 *
 * <p>Not one of Thaumaturge's accessories: those are drawn from one fixed atlas of five ids, and their item
 * class is {@code final} and knows nothing about AE2 links. The link lives in the golem's persistent data
 * instead, which survives a chunk unload and a server restart - but not "pick the golem up", which returns a
 * placer item carrying only the golem's properties and experience.
 *
 * <p>Persistent data is not synced, so {@link GolemBackpackTickHandler} sends the skin and the connection
 * state to everyone tracking the golem.
 */
@EventBusSubscriber(modid = ThEIds.MODID)
public final class GolemBackpackHandler {

    /** Where the linked network lives. The same shape AE2 stores on the item. */
    static final String KEY_LINK = "ThEWifiBackpackLink";
    /** The skin's ordinal in {@link BackpackSkins}. */
    static final String KEY_SKIN = "ThEBackpackSkin";
    /** The block the skin was chosen with, kept so it can be handed back. */
    static final String KEY_FACADE = "ThEBackpackFacade";

    /**
     * Golem UUID to decoded link, so the tick handler does not parse the same NBT twenty times a second.
     * Weakly keyed, and refreshed by every writer below.
     */
    private static final Map<UUID, GlobalPos> LINK_CACHE = Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * Set {@code THAUMICENERGISTICS_BACKPACK_TRACE} to have every equip, removal, repaint and transfer
     * print a line. The same switch-by-environment-variable the rest of this mod's machines use, and for
     * the same reason: a golem on a server is hard to watch, and "nothing happened" is not a report.
     */
    static final boolean TRACE = System.getenv("THAUMICENERGISTICS_BACKPACK_TRACE") != null;

    private GolemBackpackHandler() {}

    private static void trace(String message) {
        if (TRACE) {
            ThaumicEnergistics.LOG.info("[pack] " + message);
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
            // Client first, and only to swing: the server is where anything happens. Vanilla sends the
            // interaction packet before this event either way, so cancelling locally does not hide the
            // click from the server.
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
            // Sneak, because the bell on its own is Thaumaturge's follow toggle and sneaking with it is
            // how a golem is picked up - this branch has to run before that one, and cancelling is what
            // stops the golem being pocketed along with its backpack.
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

    /** @return whether the golem ended up wearing the backpack. */
    private static boolean equip(EntityThaumaturgeGolem golem, Player player, ItemStack held,
            ItemGolemWirelessBackpack backpack) {
        if (!owns(golem, player) || hasBackpack(golem)) {
            return false;
        }
        GlobalPos link = backpack.getLinkedPosition(held);
        if (link == null) {
            // Unlinked backpacks are equipable in the reference build only in the sense that nothing
            // happens: a backpack with no network is a decoration, and this refuses it rather than
            // letting a player wonder why the golem never reaches anything.
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

    /** @return whether there was a backpack to take off. */
    private static boolean dismantle(EntityThaumaturgeGolem golem, Player player) {
        if (!owns(golem, player)) {
            return false;
        }
        GlobalPos link = getLink(golem);
        if (link == null) {
            return false;
        }

        ItemStack backpack = new ItemStack(thaumicenergistics.init.ModItems.GOLEM_WIFI_BACKPACK.get());
        backpack.set(AEComponents.WIRELESS_LINK_TARGET, link);
        golem.spawnAtLocation(backpack);

        // The block the skin was chosen with goes back too - it was a real item the player spent, and
        // holding on to it would make repainting a golem quietly consume the block. Creative gets
        // nothing back, which is how the rest of the mod treats creative.
        ItemStack facade = getFacade(golem);
        if (!facade.isEmpty() && !player.isCreative()) {
            golem.spawnAtLocation(facade);
        }

        clear(golem);
        trace("removed the backpack from golem " + golem.getId() + ", link " + link + " returned");
        playEquipSound(golem);
        return true;
    }

    /** @return whether the skin changed. */
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
     * The reference build's sound, kept because it is the one a player already associates with putting
     * something on a golem. Thaumaturge has its own clack for accessories; this is a backpack, not one.
     */
    private static void playEquipSound(EntityThaumaturgeGolem golem) {
        golem.level().playSound(null, golem.getX(), golem.getY(), golem.getZ(),
                SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.NEUTRAL, 0.5F, 1.0F);
    }

    // ==================== the golem's own data ====================

    public static boolean hasBackpack(EntityThaumaturgeGolem golem) {
        return golem.getPersistentData().contains(KEY_LINK);
    }

    /** The network this golem's backpack points at, or null if it is not wearing one. */
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

    /** The block the skin came from, or an empty stack. */
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

    /** Forgets the backpack entirely: the link, the skin and the block behind it. */
    public static void clear(EntityThaumaturgeGolem golem) {
        CompoundTag data = golem.getPersistentData();
        data.remove(KEY_LINK);
        data.remove(KEY_SKIN);
        data.remove(KEY_FACADE);
        LINK_CACHE.remove(golem.getUUID());
    }
}
