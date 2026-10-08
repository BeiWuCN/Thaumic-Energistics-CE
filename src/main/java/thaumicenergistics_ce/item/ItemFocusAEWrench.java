package thaumicenergistics_ce.item;

import com.leclowndu93150.thaumaturge.api.spell.CastStyle;
import com.leclowndu93150.thaumaturge.api.spell.Spell;
import com.leclowndu93150.thaumaturge.api.spell.SpellNode;
import com.leclowndu93150.thaumaturge.api.spell.Spells;
import com.leclowndu93150.thaumaturge.content.spell.item.FocusItem;
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
import thaumicenergistics_ce.focus.FocusElements;

/**
 * AE2 扳手当法杖核心用；这是物品那一半，{@link FocusEffectAEWrench} 是行为那一半。
 * 法术写在物品堆的数据组件上，不在操纵器处构建：{@link #assemble} 每个 tick 都会装一次。
 * 不能从 {@code getDefaultInstance} 装：它返回 {@code new ItemStack(this)}，
 * 该构造器复制物品堆的组件，写在它上面的组件永远到不了调用方。
 */
public class ItemFocusAEWrench extends FocusItem {

    /** 部件的复杂度，写在 {@code spell_part/aewrench.json} 里；两处必须同步。 */
    private static final int COMPLEXITY = 10;

    /** Thaumaturge 每点复杂度的 vis 价，也就是旧的「复杂度除以五」那条规则。 */
    private static final float VIS_PER_COMPLEXITY = 0.2F;

    /** 写进去的法术：每次施法都从原点起，接着是扳手效果。它是不可变记录，每次调用现建。 */
    public static Spell wrenchSpell() {
        return new Spell(CastStyle.INSTANT,
                SpellNode.of(Spell.ORIGIN).then(SpellNode.of(FocusElements.AEWRENCH)));
    }

    /** 一次扳手使用的代价，按部件复杂度算：10 乘以 0.2 得 2.0。
     * 这是给潜行左键那次“转方块”看的价，右键施法的收费由法术本身走法杖的正常路径扣，
     * 与这里无关；法杖自身的收费为零，不再单独扣一次。 */
    public static float visCost() {
        return COMPLEXITY * VIS_PER_COMPLEXITY;
    }

    /** 把法术写进还没有法术的物品堆，这是唯一设置法术的位置。
     * 幂等，调用方不用管物品堆有没有过这里。
     * @return 这次写入了就是 true；已有法术或为空是 false
     */
    public static boolean assemble(ItemStack stack) {
        if (stack.isEmpty() || Spells.spellOf(stack) != null) {
            return false;
        }
        Spells.setSpell(stack, wrenchSpell());
        return true;
    }

    /** 一个已写好法术的物品堆，给挂不上 tick 组装的调用方（创造模式标签页）。 */
    public static ItemStack assembledStack() {
        ItemStack stack = new ItemStack(thaumicenergistics_ce.init.ModItems.FOCUS_AEWRENCH.get());
        assemble(stack);
        return stack;
    }

    public ItemFocusAEWrench(Item.Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        // 这里也要做，不能只靠 tick：直接来自配方结果的物品堆从没被 tick 过。
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
