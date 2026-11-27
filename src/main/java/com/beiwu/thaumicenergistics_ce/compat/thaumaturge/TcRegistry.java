package com.beiwu.thaumicenergistics_ce.compat.thaumaturge;

import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.content.essentia.jar.BlockEntityJar;
import com.leclowndu93150.thaumaturge.content.item.PhialItem;
import com.leclowndu93150.thaumaturge.content.taint.item.EssentiaCrystalFactory;
import com.leclowndu93150.thaumaturge.content.taint.item.ItemEssentiaCrystal;
import com.leclowndu93150.thaumaturge.registry.TTBlocks;
import com.leclowndu93150.thaumaturge.registry.TTDataComponents;
import com.leclowndu93150.thaumaturge.registry.TTItems;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Thaumaturge 的物品注册表，以及本 mod 用它构建的物品堆。
 * 注册表条目会变动，ALCHEMICAL_FURNACE 在 0.4.7 里被直接删掉。
 * 本包之外不要指名 {@code TTItems} 或 {@code TTDataComponents} 的字段。
 */
public final class TcRegistry {
    private TcRegistry() {}

    // -- 源质水晶 ---------------------------------------------------

    public static boolean isCrystal(ItemStack stack) {
        return !stack.isEmpty() && stack.is(TTItems.ESSENTIA_CRYSTAL.get());
    }

    /** 水晶携带的要素；空水晶或未配置水晶为 null。 */
    public static @Nullable Holder<IAspect> crystalAspect(ItemStack stack) {
        return isCrystal(stack) ? ItemEssentiaCrystal.aspectOf(stack) : null;
    }

    public static ItemStack crystalStack(Holder<IAspect> aspect, int count) {
        ItemStack stack = new ItemStack(TTItems.ESSENTIA_CRYSTAL.get(), Math.max(1, count));
        stack.set(TTDataComponents.CRYSTAL_ASPECT.get(), new AspectInstance(aspect, 1));
        return stack;
    }

    public static ItemStack crystalFor(Holder<IAspect> aspect, int amount) {
        return EssentiaCrystalFactory.of(aspect, amount);
    }

    // -- 监控器的书 --------------------------------------------------

    /** 监控器据以读取的典籍；按物品识别，不按类。 */
    public static boolean isThaumonomicon(ItemStack stack) {
        return stack.is(TTItems.THAUMONOMICON.get());
    }

    // -- 源质容器 -------------------------------------------------

    /**
     * 能不能装源质，按能力问，不按类识别。
     * <p>安瓿与罐子在 Thaumaturge 里注册的是同一个 {@code EssentiaCapabilities.CONTAINER}
     * （{@code TTCapabilities} 把 PHIAL 与 JAR_NORMAL、JAR_VOID 放进同一次 registerItem），
     * 内容读写都走 {@code IItemEssentia} 的 getAspects / setAspects。
     * 所以这里按能力判断：以后 Thaumaturge 再加别的源质容器，这里自动认得。
     * <p>但有三种同能力物品必须按物品排掉：
     * <ul>
     *   <li>虚空罐：装进去的源质会被销毁，不保留内容。</li>
     *   <li>要素水晶与魔力豆：它们也注册了 CONTAINER，但写回时数量被硬编码成 1
     *       （{@code ComponentEssentia.crystal} 的 writer），拿来当容器会
     *       抽走一把源质却只记 1 点，而且倒空后的无要素水晶/魔力豆还会被
     *       {@code inventoryTick} 随机重新赋要素，能反复刷。</li>
     * </ul>
     * 创造罐与其余罐类物品（护符、缸中之脑、节点罐）没有注册物品级 CONTAINER，
     * 天然不在名单里；将来它们若注册，会经能力自动进入——到时再评估是否保留。
     */
    public static boolean isEssentiaContainer(ItemStack stack) {
        if (stack.isEmpty()
                || stack.is(TTItems.JAR_VOID.get())
                || stack.is(TTItems.ESSENTIA_CRYSTAL.get())
                || stack.is(TTItems.MANA_BEAN.get())) {
            return false;
        }
        return stack.getCapability(
                com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities.CONTAINER) != null;
    }

    /**
     * 这只容器能装多少源质。安瓿整瓶 10；罐子按 Thaumaturge 的 {@code IEssentiaJar} 报的容量。
     * <p>{@code IEssentiaJar} 是方块实现的（{@code AbstractBlockJar implements IEssentiaJar}），
     * 物品那一侧是 {@code BlockItem}，所以要绕一遭 {@code getBlock()}。
     */
    public static int capacityOf(ItemStack stack) {
        if (stack.getItem() instanceof PhialItem) {
            return phialCapacity();
        }
        if (stack.getItem() instanceof net.minecraft.world.item.BlockItem blockItem
                && blockItem.getBlock()
                        instanceof com.leclowndu93150.thaumaturge.api.essentia.IEssentiaJar jar) {
            return jar.jarCapacity();
        }
        // 既不是安瓿也不是罐子：不是容器，容量无意义，报 0 让调用方据 0 拒绝。
        return 0;
    }

    public static boolean isPhial(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof PhialItem;
    }

    public static int jarCapacity() {
        return BlockEntityJar.CAPACITY;
    }

    public static int phialCapacity() {
        return PhialItem.BASE_AMOUNT;
    }

    public static ItemStack filledPhial(Holder<IAspect> aspect, int amount) {
        return PhialItem.makeFilled(aspect, amount);
    }

    /** 一堆空瓶子：倒空后瓶子消耗掉自己的物品 id，不会残留。 */
    public static ItemStack emptyPhials(int count) {
        return new ItemStack(TTItems.PHIAL.get(), count);
    }

    // -- 大脑 --------------------------------------------------------------

    /** 缸中之脑，概率之箱唯一接受的物品。 */
    public static boolean isJarBrain(ItemStack stack) {
        return !stack.isEmpty() && stack.is(TTItems.JAR_BRAIN.get());
    }

    /** 缸中之脑放下的声音，插入时也用这个音效。 */
    public static SoundEvent jarBrainPlaceSound() {
        return TTBlocks.JAR_BRAIN.get().defaultBlockState().getSoundType().getPlaceSound();
    }

    /** 单独的大脑，交还回去的箱子要用。 */
    public static ItemStack jarBrainStack() {
        return new ItemStack(TTItems.JAR_BRAIN.get());
    }

    public static ItemStack emptyJarStack() {
        return new ItemStack(TTItems.JAR_NORMAL.get());
    }

    public static ItemStack zombieBrainStack() {
        return new ItemStack(TTItems.BRAIN.get());
    }
}
