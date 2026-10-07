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
 * AE2 扳手当法杖核心用；这是物品那一半，{@link FocusEffectAEWrench} 是行为那一半。
 * 包写在物品堆上，不在操纵器处构建：{@link #assemble} 每个 tick 都会装一次。
 * 不能从 {@code getDefaultInstance} 装：它返回 {@code new ItemStack(this)}，
 * 该构造器复制物品堆的组件，写在它上面的组件永远到不了调用方。
 */
public class ItemFocusAEWrench extends ItemFocus {

    /** 每次法杖施法都从根介质开始：没有它 {@code CastExecutor} 没有目标，
     * 施法什么都不做，却照样扣 vis。 */
    private static final ResourceLocation ROOT = ResourceLocation.fromNamespaceAndPath("thaumaturge", "root");

    /** 组装好的包：先根介质，再扳手效果。每次调用都新建，它是不变 record；
     * {@code complexity} 要显式设，构建器默认给 0。 */
    public static FocusPackage wrenchPackage() {
        int complexity = rootComplexity() + new FocusEffectAEWrench().complexity(FocusSettings.empty());
        return FocusPackage.builder()
                .add(ROOT)
                .add(FocusEffectAEWrench.KEY)
                .complexity(complexity)
                .build();
    }

    /** 根介质自己的复杂度，从注册表读，跟真实要素不会脱节。 */
    private static int rootComplexity() {
        var element = FocusEngine.element(ROOT);
        return element == null ? 0 : element.complexity(FocusSettings.defaults(element));
    }

    /** 一次扳手使用的代价：包复杂度除以五，即 Thaumaturge 对核心的规则（{@code ItemFocus.getVisCost}）。
     * {@link FocusEffectAEWrench} 在扳手动作之后收它；{@link #getVisCost} 说明法杖自身的收费为零。 */
    public static float visCost() {
        return wrenchPackage().complexity() / 5.0F;
    }

    /** 把包写进还没有包的物品堆，这是唯一设置包的位置。
     * 幂等，调用方不用管物品堆有没有过这里。
     * @return 这次写入了就是 true；已有包或为空是 false
     */
    public static boolean assemble(ItemStack stack) {
        if (stack.isEmpty() || ItemFocus.getPackage(stack) != null) {
            return false;
        }
        ItemFocus.setPackage(stack, wrenchPackage());
        return true;
    }

    /** 一个写好包的物品堆，给挂不上 tick 组装的调用方（创造模式标签页）。 */
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
            // 返回 pass，不返回 success：success 会吞掉这次点击，什么都没装上。
            return InteractionResultHolder.pass(stack);
        }

        if (!level.isClientSide()) {
            // 这里也要做，不能只靠 tick：直接来自配方结果的物品堆从没被 tick 过。
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
