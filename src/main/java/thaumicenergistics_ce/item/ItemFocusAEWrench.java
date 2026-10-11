package thaumicenergistics_ce.item;

import com.leclowndu93150.thaumaturge.api.spell.CastStyle;
import com.leclowndu93150.thaumaturge.api.spell.Spell;
import com.leclowndu93150.thaumaturge.api.spell.SpellNode;
import com.leclowndu93150.thaumaturge.api.spell.Spells;
import com.leclowndu93150.thaumaturge.content.spell.item.FocusItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.compat.thaumaturge.TcWand;
import thaumicenergistics_ce.focus.FocusEffectAEWrench;
import thaumicenergistics_ce.focus.FocusElements;

public class ItemFocusAEWrench extends FocusItem {

    private static final int COMPLEXITY = 10;

    private static final float VIS_PER_COMPLEXITY = 0.2F;

    public static Spell wrenchSpell() {
        return new Spell(CastStyle.INSTANT,
                SpellNode.of(Spell.ORIGIN).then(SpellNode.of(FocusElements.AEWRENCH)));
    }

    public static float visCost() {
        return COMPLEXITY * VIS_PER_COMPLEXITY;
    }

    public static boolean assemble(ItemStack stack) {
        if (stack.isEmpty() || Spells.spellOf(stack) != null) {
            return false;
        }
        Spells.setSpell(stack, wrenchSpell());
        return true;
    }

    public static ItemStack assembledStack() {
        ItemStack stack = new ItemStack(thaumicenergistics_ce.init.ModItems.FOCUS_AEWRENCH.get());
        assemble(stack);
        return stack;
    }

    public ItemFocusAEWrench(Item.Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot) {
        assemble(stack);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        InteractionHand other =
                hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack otherStack = player.getItemInHand(other);

        if (!TcWand.isWand(otherStack)) {
            return InteractionResult.PASS;
        }
        if (!TcWand.focus(otherStack).isEmpty()) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide()) {
            assemble(stack);
            TcWand.setFocus(otherStack, stack.copyWithCount(1));
            level.playSound(null, player.blockPosition(), SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.PLAYERS,
                    0.45F, 1.2F);
            if (!player.isCreative()) {
                stack.shrink(1);
            }
        }
        return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
    }
}
