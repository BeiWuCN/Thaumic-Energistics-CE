package thaumicenergistics.item;

import com.leclowndu93150.thaumaturge.api.casters.FocusPackage;
import com.leclowndu93150.thaumaturge.api.casters.FocusSettings;
import com.leclowndu93150.thaumaturge.content.casters.ItemFocus;
import com.leclowndu93150.thaumaturge.content.wands.ItemWand;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import thaumicenergistics.focus.FocusEffectAEWrench;

/**
 * The AE2 wrench, as a focus you can put in a wand.
 *
 * <p>This is the item half of the focus; {@link FocusEffectAEWrench} is what it does.
 *
 * <h2>It ships assembled</h2>
 *
 * <p>The package - the programme the wand actually casts - is written onto the stack when the stack is
 * made, not left for the player to build at a focal manipulator. A wrench is a utility rather than a spell:
 * the manipulator's complexity, XP and crystal costs exist to price an effect you designed, and there is
 * nothing here to design. The original 1.7.10 focus was an ordinary craftable item for the same reason.
 *
 * <p>The package is installed by {@link #assemble}, which runs on every tick and again just before the
 * focus is put into a wand.
 *
 * <h2>Why not {@code getDefaultInstance}</h2>
 *
 * <p>That looked like the one place every route to a stack goes through, and it is not.
 * {@code Item.getDefaultInstance()} is {@code new ItemStack(this)} and the {@code ItemStack} constructor
 * copies the components of the stack that method returns - so an override that calls {@code super} and then
 * sets a component on the result sets it on a <em>different</em> stack than the caller ends up holding. Every
 * stack arrived without a package, and the item looked exactly like one that had it. This was found by the
 * gear self-test asserting on a freshly constructed stack, which is the only reason it is not still here.
 */
public class ItemFocusAEWrench extends ItemFocus {

    /**
     * The root medium every wand cast starts from, and the one Thaumaturge's own foci name first.
     *
     * <p>Without it the package holds an effect and nothing else, and {@code CastExecutor} applies effects
     * only to the targets a medium produced: {@code CastStreams.targets()} stays null, {@code applyEffect}
     * returns {@code CastStreams.EMPTY}, and the wand spends vis on a cast that does nothing. The symptom is
     * a focus that is silent, not one that errors.
     */
    private static final ResourceLocation ROOT = ResourceLocation.fromNamespaceAndPath("thaumaturge", "root");

    /**
     * The assembled package: root medium, then the wrench effect.
     *
     * <p>Built fresh each time rather than cached. {@code FocusPackage} is an immutable record and the
     * caster id is deliberately absent, so two calls produce equal values; a static field would only be one
     * more thing to invalidate.
     *
     * <p>{@code complexity} is set explicitly and is the vis price: {@link #visCost()} reads it as
     * {@code complexity / 5}, Thaumaturge's own rule, and {@code FocusPackage.Builder} defaults it to
     * <b>0</b> rather than deriving it from the units. A package built without it casts for nothing,
     * which is a silently free spell rather than an error - the gear self-test asserts on it for that
     * reason. The figure is the sum of the parts' own {@code complexity()} values, which is how
     * Thaumaturge's manipulator arrives at one: the root medium contributes 10 and the wrench effect 10,
     * so 20, which prices a use at 4 vis.
     */
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
        var element = com.leclowndu93150.thaumaturge.api.casters.FocusEngine.element(ROOT);
        return element == null ? 0 : element.complexity(FocusSettings.defaults(element));
    }

    /**
     * What one wrench use costs: the package's complexity over five, Thaumaturge's rule for a focus
     * ({@code ItemFocus.getVisCost}). Derived from the package rather than written down here, so the price
     * cannot drift from what the wand casts.
     *
     * <p>{@link FocusEffectAEWrench} charges this once a wrench has acted; the wand's own charge cannot,
     * because it runs before the cast is known to do anything - see {@link #getVisCost}.
     */
    public static float visCost() {
        return wrenchPackage().complexity() / 5.0F;
    }

    /**
     * Writes the package onto a stack that has none. Idempotent and cheap after the first call, so callers
     * do not have to know whether a stack has been through here. This is the only place a package is set.
     *
     * @return true if it wrote one now, false if the stack already had one or is empty
     */
    public static boolean assemble(ItemStack stack) {
        if (stack.isEmpty() || ItemFocus.getPackage(stack) != null) {
            return false;
        }
        ItemFocus.setPackage(stack, wrenchPackage());
        return true;
    }

    /**
     * A stack with the package already written.
     *
     * <p>For callers that present the item rather than hand it out - the creative tab, a self-test - where
     * there is no tick and no use to hang the assembly off. Written as one call so no caller has to remember
     * to do it.
     */
    public static ItemStack assembledStack() {
        ItemStack stack = new ItemStack(thaumicenergistics.init.ModItems.FOCUS_AEWRENCH.get());
        assemble(stack);
        return stack;
    }

    public ItemFocusAEWrench(Item.Properties properties) {
        super(properties, 0);
    }

    /**
     * Zero, deliberately: {@code ItemWand.use} charges a focus before the cast runs, when nothing is known
     * about what the caster is looking at, so any price here is spent on a cast that takes nothing apart.
     * The real price is {@link #visCost()}, charged by {@link FocusEffectAEWrench} once a wrench action has
     * actually happened.
     *
     * <p>The wand's tooltip and the caster HUD read this same method for their figure, so they show the
     * up-front price - nothing - rather than the price of a use.
     */
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

        if (!(otherStack.getItem() instanceof ItemWand wand)) {
            return InteractionResultHolder.pass(stack);
        }
        if (!wand.getFocusStack(otherStack).isEmpty()) {
            // The wand already holds a focus. Succeeding without installing anything would swallow the
            // click and tell the player nothing, so pass and let the wand have it.
            return InteractionResultHolder.pass(stack);
        }

        if (!level.isClientSide()) {
            // Assembled here as well as on the tick: a stack taken straight from a recipe result and used
            // has never been ticked, and would otherwise go into the wand inert.
            assemble(stack);
            wand.setFocus(otherStack, stack.copyWithCount(1));
            level.playSound(null, player.blockPosition(), SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.PLAYERS,
                    0.45F, 1.2F);
            if (!player.isCreative()) {
                stack.shrink(1);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.thaumicenergistics.focus_aewrench.desc")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.thaumicenergistics.focus_aewrench.install")
                .withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
