package thaumicenergistics_ce.focus;

import appeng.core.definitions.AEItems;
import appeng.hooks.WrenchHook;
import appeng.util.InteractionUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Everything in AE2 that just needs "a wrench was used here".
 * <ul>
 * <li>AE2 has one entry point, {@link WrenchHook}; a stack is a wrench by the {@code c:tools/wrench} tag.
 * <li>So this swaps a real wrench in, asks AE2 to do the thing, and puts the player's item back.
 * <li>Server-only and always restored: a client-side {@code setItemInHand} would desync the prediction.
 * </ul>
 */
public final class AEWrench {

    private AEWrench() {}

    /**
     * Runs AE2's wrench action against one block, as if the player held a quartz wrench. Server only.
     * @return true if AE2 did something; false means the block is not one a wrench acts on.
     */
    public static boolean use(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide() || player.isSpectator()) {
            return false;
        }

        ItemStack wrench = wrenchStack();
        if (wrench.isEmpty()) {
            // AE2 is a hard dependency, so this only happens if its wrench is renamed or removed.
            return false;
        }

        ItemStack held = player.getItemInHand(hand);
        try {
            player.setItemInHand(hand, wrench);
            InteractionResult result = WrenchHook.onPlayerUseBlock(player, level, hand, hit);
            return result.consumesAction();
        } finally {
            player.setItemInHand(hand, held);
        }
    }

    /**
     * The wrench this focus borrows. AE2 routes a wrench action to
     * {@code AEBaseBlockEntity.disassembleWithWrench}, which is what makes this a disassembly.
     */
    public static ItemStack wrenchStack() {
        return AEItems.CERTUS_QUARTZ_WRENCH.stack();
    }

    /**
     * Whether AE2 would treat a stack as a wrench; used by the self-test. A rename of
     * {@code c:tools/wrench} would make this focus do nothing, silently.
     */
    public static boolean isWrench(ItemStack stack) {
        return InteractionUtil.canWrenchDisassemble(stack);
    }
}
