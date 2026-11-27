package com.beiwu.thaumicenergistics_ce.integration.ae2;

import appeng.api.behaviors.ContainerItemStrategies;
import appeng.api.behaviors.ContainerItemStrategy;
import appeng.api.config.Actionable;
import appeng.api.stacks.GenericStack;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.content.essentia.item.ComponentEssentia;
import com.leclowndu93150.thaumaturge.content.item.PhialItem;
import com.leclowndu93150.thaumaturge.registry.TTSounds;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import com.beiwu.thaumicenergistics_ce.util.EssentiaContainers;

/**
 * 让 AE2 把装源质的容器（安瓿）当成它认识的那种「容器物品」。
 * <p>注册之后 {@code ContainerItemStrategies.getContainedStack(安瓿)} 就能问出里面的源质，
 * 和「一桶水问出水」是同一条路：过滤槽、分区槽、样板里的替换都会走它，
 * 不必每个界面各写一份 instanceof 判断。
 * <p>
 * 装填和抽出都支持，但按安瓿本来的语义走：<b>整瓶进出</b>。
 * {@link PhialItem} 一次装 {@link PhialItem#BASE_AMOUNT} 单位、倒一次给一整瓶，
 * 游戏里没有任何途径做出「半瓶」。这里允许部分量会造出原版交互认不出的物品，
 * 所以要么整瓶成交，要么报 0 一次都不做。
 * <p>
 * 手持整叠安瓿也能用：抽/灌都只动其中一瓶，剩下那几瓶回到背包，
 * 和 AE2 自己的容器物品（见 {@code FluidContainerItemStrategy}）一个做法。
 * <p>
 * 换物品照 AE2 自己的做法（见 {@code FluidContainerItemStrategy}）：
 * 把手上那一瓶 {@code shrink(1)} 掉，再把换来的那一瓶塞进同一个位置。
 * 原地改组件不行——装和倒会换物品身份，空瓶和满瓶在 AE2 眼里是两种 key。
 */
public final class EssentiaContainerStrategy
        implements ContainerItemStrategy<AEssentiaKey, EssentiaContainerStrategy.Carried> {

    /** 一次装多少。跟着安瓿自己走，不在这里另立一份数字。 */
    private static final int CHARGE = PhialItem.BASE_AMOUNT;

    /**
     * 携带上下文：某一格里的那一件容器物品。
     * 光标和玩家背包槽各一套，装填完把换来的物品放回原处。
     */
    public sealed interface Carried permits CarriedOnCursor, CarriedInSlot {}

    /** 光标上那一件。 */
    public record CarriedOnCursor(Player player, AbstractContainerMenu menu) implements Carried {}

    /** 玩家背包第 {@code slot} 格那一件。 */
    public record CarriedInSlot(Player player, int slot) implements Carried {}

    private EssentiaContainerStrategy() {}

    /** 在公共初始化里调一次；重复注册会覆盖同一 key 类型的策略。 */
    public static void register() {
        ContainerItemStrategies.register(
                AEssentiaKeyType.INSTANCE, AEssentiaKey.class, new EssentiaContainerStrategy());
    }

    @Override
    public @Nullable GenericStack getContainedStack(ItemStack stack) {
        return EssentiaContainers.contained(stack);
    }

    @Override
    public @Nullable Carried findCarriedContext(Player player, AbstractContainerMenu menu) {
        ItemStack carried = menu.getCarried();
        // 只认真的装得下源质的那一种物品，别把光标上的石头也当成容器。
        return isEssentiaContainer(carried) ? new CarriedOnCursor(player, menu) : null;
    }

    @Override
    public @Nullable Carried findPlayerSlotContext(Player player, int slot) {
        if (slot < 0 || slot >= player.getInventory().getContainerSize()) {
            return null;
        }
        ItemStack stack = player.getInventory().getItem(slot);
        return isEssentiaContainer(stack) ? new CarriedInSlot(player, slot) : null;
    }

    @Override
    public long extract(Carried context, AEssentiaKey what, long amount, Actionable mode) {
        ItemStack stack = stackOf(context);
        if (stack.isEmpty() || !menuAllows(context)) {
            return 0;
        }
        AspectList stored = ComponentEssentia.phial(stack).getAspects();
        if (stored.entries().size() != 1) {
            return 0;
        }
        AspectInstance entry = stored.entries().getFirst();
        // 要的量和瓶里的不是同一味，或要不满一整瓶：都不做。
        AEssentiaKey storedKey = AEssentiaKey.of(entry.aspect());
        if (storedKey == null || !storedKey.equals(what) || amount < entry.amount()) {
            return 0;
        }
        if (mode == Actionable.MODULATE) {
            // 空瓶仍是安瓿本身，只是去掉内容物组件。
            ItemStack empty = new ItemStack(stack.getItem());
            swap(context, empty);
            playEmptySound(playerOf(context), what);
        }
        return entry.amount();
    }

    @Override
    public long insert(Carried context, AEssentiaKey what, long amount, Actionable mode) {
        ItemStack stack = stackOf(context);
        if (stack.isEmpty() || !menuAllows(context)) {
            return 0;
        }
        // 瓶里已经有东西就不能再灌，混装没有唯一答案。
        if (!ComponentEssentia.phial(stack).getAspects().isEmpty()) {
            return 0;
        }
        Holder<IAspect> aspect = what.resolveAspect();
        if (aspect == null || amount < CHARGE) {
            return 0;
        }
        if (mode == Actionable.MODULATE) {
            ItemStack filled = PhialItem.makeFilled(aspect, CHARGE);
            swap(context, filled);
            playFillSound(playerOf(context), what);
        }
        return CHARGE;
    }

    /** 瓶里能取出来的那一味；空瓶返回 {@code null}。 */
    @Override
    public @Nullable GenericStack getExtractableContent(Carried context) {
        return EssentiaContainers.contained(stackOf(context));
    }

    @Override
    public void playFillSound(Player player, AEssentiaKey what) {
        playJarSound(player);
    }

    @Override
    public void playEmptySound(Player player, AEssentiaKey what) {
        playJarSound(player);
    }

    // 内部

    private static void playJarSound(Player player) {
        // 和服务端安瓿交互同一个声音，玩家听不出两条路径的区别。
        player.level()
                .playSound(
                        null,
                        player.getX(),
                        player.getY(),
                        player.getZ(),
                        TTSounds.JAR.get(),
                        SoundSource.PLAYERS,
                        0.25F,
                        1.0F);
    }

    /**
     * 带访问卡的终端会不会允许这次容器转移。
     * <p>AE2 把它自己的 {@code FILL_ITEM} / {@code EMPTY_ITEM} 直接交给容器策略，
     * 不经过终端界面的手势层。不在这里再问一次，无卡的奥术终端仍能靠原生点击
     * 把瓶子灌满或倒空，那么「无卡只看不能动」就只是界面上说说。
     * <p>只有源质终端家族受这个门槛约束；普通 ME 终端、样板供应器不受影响。
     */
    private static boolean menuAllows(Carried context) {
        Player player = playerOf(context);
        var menu = context instanceof CarriedOnCursor cursor ? cursor.menu() : player.containerMenu;
        return !(menu instanceof com.beiwu.thaumicenergistics_ce.menu.MenuEssentiaTerminalBase terminal)
                || terminal.essentiaInteractionAllowed();
    }

    private static ItemStack stackOf(Carried context) {
        return switch (context) {
            case CarriedOnCursor cursor -> cursor.menu().getCarried();
            case CarriedInSlot inSlot -> inSlot.player().getInventory().getItem(inSlot.slot());
        };
    }

    /** 两套上下文共用的取玩家方法。 */
    private static Player playerOf(Carried context) {
        return switch (context) {
            case CarriedOnCursor cursor -> cursor.player();
            case CarriedInSlot inSlot -> inSlot.player();
        };
    }

    /**
     * 把这一格的容器换成 {@code replacement}。
     * 空的直接放回原位，非空说明还有别的瓶子叠在那，多出来的塞进背包。
     */
    private static void swap(Carried context, ItemStack replacement) {
        switch (context) {
            case CarriedOnCursor cursor -> {
                AbstractContainerMenu menu = cursor.menu();
                ItemStack current = menu.getCarried();
                if (current.getCount() > 1) {
                    current.shrink(1);
                    cursor.player().getInventory().placeItemBackInInventory(replacement);
                } else {
                    menu.setCarried(replacement);
                }
            }
            case CarriedInSlot inSlot -> {
                var inventory = inSlot.player().getInventory();
                ItemStack current = inventory.getItem(inSlot.slot());
                if (current.getCount() > 1) {
                    current.shrink(1);
                    inventory.placeItemBackInInventory(replacement);
                } else {
                    inventory.setItem(inSlot.slot(), replacement);
                }
            }
        }
    }

    /**
     * 这件物品能不能装源质。看物品类型而不是当前内容：
     * 空安瓿还没有 {@code ASPECTS} 组件，只看内容会把「能不能灌」判成「不能」。
     * <p><b>注意这里不能换成 {@code TcRegistry.isEssentiaContainer}。</b>
     * 那个判据按能力识别，安瓿与罐子都算；而这一侧的 extract / insert 是用
     * {@code PhialItem.makeFilled} 造物品的，把罐子放进来会被作成安瓿。
     * 罐子的填充与倒空走 {@code EssentiaFillHelper} 的 {@code IItemEssentia} 路径
     * （getAspects / setAspects 原地改组件），不经过这里。
     * 要两边统一，得先把这一侧也改成 {@code IItemEssentia} 写法。
     */
    private static boolean isEssentiaContainer(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof PhialItem;
    }
}
