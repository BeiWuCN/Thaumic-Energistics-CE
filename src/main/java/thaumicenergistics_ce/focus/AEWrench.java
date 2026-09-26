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
 *
 * <p>AE2 has one entry point for a wrench action - {@link WrenchHook} - and decides whether a stack is a
 * wrench purely by the {@code c:tools/wrench} tag. So this class puts a real wrench where the game will
 * look for one, asks AE2 to do the thing, and puts the player's item back.
 *
 * <p>The swap is server-only and always restored, because {@code Player#setItemInHand} on a client changes
 * what the client predicts and sends, and a desync leaves the player holding a wrench they do not have.
 */
public final class AEWrench {

    private AEWrench() {}

    /**
     * Runs AE2's wrench action against one block, as if the player held a quartz wrench. Server only.
     *
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
