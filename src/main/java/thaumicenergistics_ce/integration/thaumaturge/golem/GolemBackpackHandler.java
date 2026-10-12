package thaumicenergistics_ce.integration.thaumaturge.golem;

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
import net.minecraft.server.level.ServerLevel;
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
import thaumicenergistics_ce.registry.TECEItems;
import thaumicenergistics_ce.item.ItemGolemWirelessBackpack;
import thaumicenergistics_ce.util.ThEItemTags;

@EventBusSubscriber(modid = ThEIds.MODID)
public final class GolemBackpackHandler {

    static final String KEY_LINK = "ThEWifiBackpackLink";
    static final String KEY_SKIN = "ThEBackpackSkin";
    static final String KEY_FACADE = "ThEBackpackFacade";

    private static final Map<UUID, GlobalPos> LINK_CACHE = Collections.synchronizedMap(new WeakHashMap<>());

    private GolemBackpackHandler() {}

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof EntityThaumaturgeGolem golem)) {
            return;
        }

        Player player = event.getEntity();
        ItemStack held = player.getItemInHand(event.getHand());
        if (held.isEmpty()) {
            if (player.isShiftKeyDown() && !event.getLevel().isClientSide()) {
                salvage(golem);
            }
            return;
        }

        if (held.getItem() instanceof ItemGolemWirelessBackpack backpack) {
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
            return false;
        }

        setLink(golem, link);
        setSkin(golem, BackpackSkins.Thaumium);

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

        releasePack(golem, link, !player.isCreative());

        playEquipSound(golem);
        return true;
    }

    private static void salvage(EntityThaumaturgeGolem golem) {
        GlobalPos link = getLink(golem);
        if (link == null) {
            return;
        }

        releasePack(golem, link, true);

        playEquipSound(golem);
    }

    private static void releasePack(EntityThaumaturgeGolem golem, GlobalPos link, boolean returnFacade) {
        ItemStack backpack = new ItemStack(TECEItems.GOLEM_WIFI_BACKPACK.get());
        backpack.set(AEComponents.WIRELESS_LINK_TARGET, link);
        drop(golem, backpack);

        ItemStack facade = getFacade(golem);
        if (returnFacade && !facade.isEmpty()) {
            drop(golem, facade);
        }

        clear(golem);
    }

    private static boolean repaint(EntityThaumaturgeGolem golem, Player player, ItemStack held,
            BackpackSkins skin) {
        if (!owns(golem, player)) {
            return false;
        }

        ItemStack previous = getFacade(golem);
        if (!previous.isEmpty() && !player.isCreative()) {
            drop(golem, previous);
        }

        golem.getPersistentData().remove(KEY_FACADE);
        setSkin(golem, skin);

        playEquipSound(golem);
        return true;
    }

    private static boolean owns(EntityThaumaturgeGolem golem, Player player) {
        return golem.ownerIdentity().map(uuid -> uuid.equals(player.getUUID())).orElse(false);
    }

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
        GlobalPos decoded = GlobalPos.CODEC.parse(NbtOps.INSTANCE, data.getCompoundOrEmpty(KEY_LINK)).result().orElse(null);
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
        return BackpackSkins.fromOrdinal(golem.getPersistentData().getIntOr(KEY_SKIN, 0));
    }

    public static void setSkin(EntityThaumaturgeGolem golem, BackpackSkins skin) {
        golem.getPersistentData().putInt(KEY_SKIN, skin.ordinal());
        GolemBackpackTickHandler.syncNow(golem);
    }

    private static void drop(EntityThaumaturgeGolem golem, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        if (golem.level() instanceof ServerLevel serverLevel) {
            golem.spawnAtLocation(serverLevel, stack);
        }
    }

    public static ItemStack getFacade(EntityThaumaturgeGolem golem) {
        CompoundTag data = golem.getPersistentData();
        if (!data.contains(KEY_FACADE)) {
            return ItemStack.EMPTY;
        }
        return ThEItemTags.loadOptional(data.getCompoundOrEmpty(KEY_FACADE), golem.registryAccess())
                .orElse(ItemStack.EMPTY);
    }

    public static void clear(EntityThaumaturgeGolem golem) {
        CompoundTag data = golem.getPersistentData();
        data.remove(KEY_LINK);
        data.remove(KEY_SKIN);
        data.remove(KEY_FACADE);
        LINK_CACHE.remove(golem.getUUID());
        GolemBackpackTickHandler.clearBackpackState(golem);
    }
}
