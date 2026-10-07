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
 * 给傀儡装上无线背包、再取下、以及重新上色：用已链接的背包装备，用潜行状态下的
 * 傀儡铃取下，用映射到的方块重新上色。它不是饰品——饰品使用固定的五 id 图集
 * 和一个没有 AE2 链接的 {@code final} 物品。链接存放在傀儡的持久化数据里，
 * 不参与同步，由
 * {@link GolemBackpackTickHandler} 推送。
 */
@EventBusSubscriber(modid = ThEIds.MODID)
public final class GolemBackpackHandler {

    /** 已链接的网络存放在哪里。与 AE2 存储在物品上的结构相同。 */
    static final String KEY_LINK = "ThEWifiBackpackLink";
    static final String KEY_SKIN = "ThEBackpackSkin";
    static final String KEY_FACADE = "ThEBackpackFacade";

    /** 傀儡 UUID 到已解码链接的映射：tick 处理器不能每秒把同一份 NBT 解析二十次。 */
    private static final Map<UUID, GlobalPos> LINK_CACHE = Collections.synchronizedMap(new WeakHashMap<>());

    /** 设置 {@code THAUMICENERGISTICS_BACKPACK_TRACE} 可记录每次装备、取下、重新上色与传输。 */
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
            // 客户端这边只负责挥手：所有实际动作都在服务端，而且无论怎样原版都会先发交互
            // 数据包再触发本事件，因此本地取消并不会让服务端看不到这次点击。
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
            // 需要潜行，因为单用铃是 Thaumaturge 的跟随开关；取消才是阻止
            // 傀儡连同背包一起被收走的原因。
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
            // 予以拒绝：没有网络的背包只是装饰，傀儡永远够不到任何东西。
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

        // 方块也会退还：它是玩家实实在在花掉的物品，重新上色不得把它消耗掉。
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
     * 参照实现所用的音效，玩家已经把它与「给傀儡装上东西」联系在一起。
     * Thaumaturge 为饰品配了自己的咔嗒声；这是背包，不属于饰品。
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
