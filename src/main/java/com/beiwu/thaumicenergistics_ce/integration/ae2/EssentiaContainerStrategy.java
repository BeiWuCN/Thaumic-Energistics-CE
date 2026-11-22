package com.beiwu.thaumicenergistics_ce.integration.ae2;

import appeng.api.behaviors.ContainerItemStrategies;
import appeng.api.behaviors.ContainerItemStrategy;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
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
 * <p>只实现「读内容物」这一半。装填、抽出要有一整套携带上下文的语义（往哪罐里倒、倒多少、
 * 放什么声音），本模组没有源质罐那样的容器交互，这些一律报 0 / null，等于「不支持装填」，
 * 而不是假装支持却什么都不做。
 */
public final class EssentiaContainerStrategy
        implements ContainerItemStrategy<AEssentiaKey, EssentiaContainerStrategy.Carried> {

    /** 携带上下文：本策略不做装填，所以它没有内容。 */
    public record Carried() {}

    private static final Carried NONE = new Carried();

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
        // 手上拿着安瓿也不算「可装填的容器」：没有地方可以倒。
        return null;
    }

    @Override
    public @Nullable Carried findPlayerSlotContext(Player player, int slot) {
        return null;
    }

    @Override
    public long extract(Carried context, AEssentiaKey what, long amount, Actionable mode) {
        return 0;
    }

    @Override
    public long insert(Carried context, AEssentiaKey what, long amount, Actionable mode) {
        return 0;
    }

    @Override
    public void playFillSound(Player player, AEssentiaKey what) {
        // 不支持装填，也就没有声音。
    }

    @Override
    public void playEmptySound(Player player, AEssentiaKey what) {
        // 同上。
    }

    @Override
    public @Nullable GenericStack getExtractableContent(Carried context) {
        return null;
    }
}
