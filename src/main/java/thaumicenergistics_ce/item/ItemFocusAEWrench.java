package thaumicenergistics_ce.item;

import com.leclowndu93150.thaumaturge.api.casters.FocusEngine;
import com.leclowndu93150.thaumaturge.api.casters.FocusPackage;
import com.leclowndu93150.thaumaturge.api.casters.FocusSettings;
import com.leclowndu93150.thaumaturge.content.casters.ItemFocus;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import thaumicenergistics_ce.compat.thaumaturge.TcWand;
import thaumicenergistics_ce.focus.FocusEffectAEWrench;

/**
 * The AE2 wrench as a wand focus; the item half, {@link FocusEffectAEWrench} is what it does.
 * <ul>
 *   <li>Written onto the stack, not built at a manipulator: {@link #assemble} installs it on every tick.
 *   <li>Not installed from {@code getDefaultInstance}: it returns {@code new ItemStack(this)}, whose
 *       constructor copies that stack's components, so a component set on it never reaches the caller.
 * </ul>
 */
public class ItemFocusAEWrench extends ItemFocus {

    /** The root medium every wand cast starts from; without it {@code CastExecutor} has no targets, so the
     * cast silently does nothing and still costs vis. */
    private static final ResourceLocation ROOT = ResourceLocation.fromNamespaceAndPath("thaumaturge", "root");

    /** The assembled package: root medium, then the wrench effect; built fresh each call, as it is an
     * immutable record, and {@code complexity} must be set because the builder defaults it to 0. */
    public static FocusPackage wrenchPackage() {
        int complexity = rootComplexity() + new FocusEffectAEWrench().complexity(FocusSettings.empty());
        return FocusPackage.builder()
                .add(ROOT)
                .add(FocusEffectAEWrench.KEY)
                .complexity(complexity)
                .build();
    }

    /** The root medium's own complexity, read from the registry so it cannot drift from the real element. */
    private static int rootComplexity() {
        var element = FocusEngine.element(ROOT);
        return element == null ? 0 : element.complexity(FocusSettings.defaults(element));
    }

    /** What one wrench use costs - the package's complexity over five, Thaumaturge's rule for a focus
     * ({@code ItemFocus.getVisCost}) - derived from the package so the price cannot drift from the cast.
     * {@link FocusEffectAEWrench} charges it after a wrench has acted; see {@link #getVisCost} for why
     * the wand's own charge is zero. */
    public static float visCost() {
        return wrenchPackage().complexity() / 5.0F;
    }

    /** Writes the package onto a stack that has none; the only place a package is set. Idempotent, so callers
     * need not know whether a stack has been through here.
     * @return true if it wrote one now, false if the stack already had one or is empty
     */
    public static boolean assemble(ItemStack stack) {
        if (stack.isEmpty() || ItemFocus.getPackage(stack) != null) {
            return false;
        }
        ItemFocus.setPackage(stack, wrenchPackage());
        return true;
    }

    /** A stack with the package already written, for callers with no tick to hang assembly off (creative
     * tab, self-test). */
    public static ItemStack assembledStack() {
        ItemStack stack = new ItemStack(thaumicenergistics_ce.init.ModItems.FOCUS_AEWRENCH.get());
        assemble(stack);
        return stack;
    }

    public ItemFocusAEWrench(Item.Properties properties) {
        super(properties, 0);
    }

    @Override
    public float getVisCost(ItemStack focusStack) {
        return 0.0F;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide()) {
            assemble(stack);
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        InteractionHand other =
                hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack otherStack = player.getItemInHand(other);

        if (!TcWand.isWand(otherStack)) {
            return InteractionResultHolder.pass(stack);
        }
        if (!TcWand.focus(otherStack).isEmpty()) {
            // Pass rather than succeed: succeeding would swallow the click without installing anything.
            return InteractionResultHolder.pass(stack);
        }

        if (!level.isClientSide()) {
            // Also here, not only on the tick: a stack straight from a recipe result has never been ticked.
            assemble(stack);
            TcWand.setFocus(otherStack, stack.copyWithCount(1));
            level.playSound(null, player.blockPosition(), SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.PLAYERS,
                    0.45F, 1.2F);
            if (!player.isCreative()) {
                stack.shrink(1);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
