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
 * AE2 扳手作为法杖核心；这是物品那一半，{@link FocusEffectAEWrench} 才是它的行为。
 * 这一半是写到物品堆上的，而不是在操纵器处构建，因为 {@link #assemble}
 * 每个 tick 都会把它装上。它不是从 {@code getDefaultInstance} 装上的，后者返回
 * {@code new ItemStack(this)}：该构造器会复制物品堆的组件，所以在它上面设置的组件
 * 永远到不了调用方。
 */
public class ItemFocusAEWrench extends ItemFocus {

    /** 每次法杖施法都从根介质开始；没有它 {@code CastExecutor} 就没有目标，于是
     * 施法静默地什么都不做，却仍然消耗 vis。 */
    private static final ResourceLocation ROOT = ResourceLocation.fromNamespaceAndPath("thaumaturge", "root");

    /** 组装好的包：先是根介质，然后是扳手效果；每次调用都新建，因为它是
     * 不可变 record，而且必须设置 {@code complexity}，因为构建器把它默认为 0。 */
    public static FocusPackage wrenchPackage() {
        int complexity = rootComplexity() + new FocusEffectAEWrench().complexity(FocusSettings.empty());
        return FocusPackage.builder()
                .add(ROOT)
                .add(FocusEffectAEWrench.KEY)
                .complexity(complexity)
                .build();
    }

    /** 根介质自身的复杂度，从注册表读取，因此不会与真实要素发生偏移。 */
    private static int rootComplexity() {
        var element = FocusEngine.element(ROOT);
        return element == null ? 0 : element.complexity(FocusSettings.defaults(element));
    }

    /** 一次扳手使用的代价——包的复杂度除以五，这是 Thaumaturge 对核心的规则（
     * {@code ItemFocus.getVisCost}）——由包推导，价格不会与施法脱节。
     * {@link FocusEffectAEWrench} 在扳手动作之后收取它；{@link #getVisCost} 还说明为何
     * 法杖自身的收费为零。 */
    public static float visCost() {
        return wrenchPackage().complexity() / 5.0F;
    }

    /** 把包写入一个还没有包的物品堆；这是唯一设置包的地方。它是幂等的，所以调用方
     * 无需知道物品堆是否已经过这里。
     * @return 如果这次写入了则返回 true，如果物品堆已有包或为空则返回 false
     */
    public static boolean assemble(ItemStack stack) {
        if (stack.isEmpty() || ItemFocus.getPackage(stack) != null) {
            return false;
        }
        ItemFocus.setPackage(stack, wrenchPackage());
        return true;
    }

    /** 一个已经写好包的物品堆，供没有 tick 可挂载组装逻辑的调用方使用（
     * 创造模式标签页）。 */
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
            // 返回 pass 而不是 success：返回 success 会吞掉这次点击，却什么都没装上。
            return InteractionResultHolder.pass(stack);
        }

        if (!level.isClientSide()) {
            // 这里也要做，不只是靠 tick：直接来自配方结果的物品堆从未被 tick 过。
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
