package thaumicenergistics_ce.compat.thaumaturge;

import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.content.essentia.jar.BlockEntityJar;
import com.leclowndu93150.thaumaturge.content.essentia.jar.JarItem;
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

    /** 只认罐子和瓶子，本 mod 会填充的两种容器。 */
    public static boolean isEssentiaContainer(ItemStack stack) {
        return !stack.isEmpty()
                && (stack.getItem() instanceof JarItem || stack.getItem() instanceof PhialItem);
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
}
