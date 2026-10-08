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
import thaumicenergistics_ce.init.ModItems;
import thaumicenergistics_ce.item.ItemGolemWirelessBackpack;
import thaumicenergistics_ce.util.ThELog;
import thaumicenergistics_ce.util.ThEItemTags;

/**
 * 给傀儡装无线背包、取下、重新上色。
 * 装备用已链接的背包，取下用潜行状态的傀儡铃，上色用映射到的方块。
 * 不做成饰品：饰品用固定的五 id 图集、{@code final} 物品，没有 AE2 链接。
 * 链接存在傀儡的持久化数据里不同步，由 {@link GolemBackpackTickHandler} 推送。
 */
@EventBusSubscriber(modid = ThEIds.MODID)
public final class GolemBackpackHandler {

    /** 已链接网络的存放位置。与 AE2 存在物品上的结构相同。 */
    static final String KEY_LINK = "ThEWifiBackpackLink";
    static final String KEY_SKIN = "ThEBackpackSkin";
    static final String KEY_FACADE = "ThEBackpackFacade";

    /** 傀儡 UUID 到已解码链接的缓存：tick 处理器每秒二十次解析同一份 NBT 太贵。 */
    private static final Map<UUID, GlobalPos> LINK_CACHE = Collections.synchronizedMap(new WeakHashMap<>());

    /** 环境变量 {@code THAUMICENERGISTICS_BACKPACK_TRACE} 打开后记录每次装备、取下、上色与传输。 */
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
            // 空手潜行右键就是 Thaumaturge 的「收起傀儡」：它把属性与经验写进一颗物品再把傀儡 discard。
            // 背包链接存在傀儡的持久化数据里，不跟着走，所以趁傀儡还在先摘下来还给玩家。
            if (player.isShiftKeyDown() && !event.getLevel().isClientSide()) {
                salvage(golem);
            }
            return;
        }

        if (held.getItem() instanceof ItemGolemWirelessBackpack backpack) {
            // 客户端这边只负责挥手，动作全在服务端。
            // 原版先发交互数据包再触发本事件，本地取消挡不住服务端这次点击。
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
            // 要求潜行，单用铃是 Thaumaturge 的跟随开关。
            // 取消事件才拦得住傀儡被连背包一起收走。
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
            // 拒绝：没有网络的背包傀儡够不到东西，只是装饰。
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

        releasePack(golem, link, !player.isCreative());
        trace("removed the backpack from golem " + golem.getId() + ", link " + link + " returned");
        playEquipSound(golem);
        return true;
    }

    /**
     * 收起傀儡（空手潜行右键）前把背包摘下来。Thaumaturge 的 {@code pickUpGolem} 只把属性与经验写进
     * 那颗「傀儡」物品，傀儡的持久化数据不跟着走，背包与链接会一起没 —— 这里不拦那次收，只先还东西。
     */
    private static void salvage(EntityThaumaturgeGolem golem) {
        GlobalPos link = getLink(golem);
        if (link == null) {
            return;
        }

        releasePack(golem, link, true);
        trace("took the backpack off golem " + golem.getId() + " before it was pocketed, link " + link);
        playEquipSound(golem);
    }

    /**
     * 把背包与方块摘下来落在傀儡脚下。方块是玩家花掉的物品，只有主动取下时才看创造模式。
     */
    private static void releasePack(EntityThaumaturgeGolem golem, GlobalPos link, boolean returnFacade) {
        ItemStack backpack = new ItemStack(ModItems.GOLEM_WIFI_BACKPACK.get());
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
     * 沿用参照实现的音效，玩家已把它和「给傀儡装上东西」绑在一起。
     * Thaumaturge 给饰品配的是另一个咔嗒声，背包不是饰品。
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
    }

    /** 26.1.2 只有 {@link ServerLevel} 会掉落物品；别处没有掉落可做。 */
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

    private static void setFacade(EntityThaumaturgeGolem golem, ItemStack facade) {
        golem.getPersistentData()
                .put(KEY_FACADE, (CompoundTag) ThEItemTags.save(facade, golem.registryAccess()));
    }

    public static void clear(EntityThaumaturgeGolem golem) {
        CompoundTag data = golem.getPersistentData();
        data.remove(KEY_LINK);
        data.remove(KEY_SKIN);
        data.remove(KEY_FACADE);
        LINK_CACHE.remove(golem.getUUID());
    }
}
