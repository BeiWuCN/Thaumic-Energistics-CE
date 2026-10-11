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

public final class TcRegistry {
    private TcRegistry() {}


    public static boolean isCrystal(ItemStack stack) {
        return !stack.isEmpty() && stack.is(TTItems.ESSENTIA_CRYSTAL.get());
    }

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


    public static boolean isThaumonomicon(ItemStack stack) {
        return stack.is(TTItems.THAUMONOMICON.get());
    }

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

    public static ItemStack emptyPhials(int count) {
        return new ItemStack(TTItems.PHIAL.get(), count);
    }


    public static boolean isJarBrain(ItemStack stack) {
        return !stack.isEmpty() && stack.is(TTItems.JAR_BRAIN.get());
    }

    public static SoundEvent jarBrainPlaceSound() {
        return TTBlocks.JAR_BRAIN.get().defaultBlockState().getSoundType().getPlaceSound();
    }

    public static ItemStack jarBrainStack() {
        return new ItemStack(TTItems.JAR_BRAIN.get());
    }
}
