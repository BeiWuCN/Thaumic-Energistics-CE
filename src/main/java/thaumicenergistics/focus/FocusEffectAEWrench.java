package thaumicenergistics.focus;

import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aspect.TCAspects;
import com.leclowndu93150.thaumaturge.api.casters.CastContext;
import com.leclowndu93150.thaumaturge.api.casters.FocusEffect;
import com.leclowndu93150.thaumaturge.api.casters.FocusElement;
import com.leclowndu93150.thaumaturge.api.casters.FocusSettings;
import com.leclowndu93150.thaumaturge.api.casters.Trajectory;
import com.leclowndu93150.thaumaturge.content.misc.TCActionBar;
import com.leclowndu93150.thaumaturge.content.wands.ItemWand;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import thaumicenergistics.ThEIds;
import thaumicenergistics.init.ModItems;
import thaumicenergistics.item.ItemFocusAEWrench;

/**
 * The AE2 wrench, as a focus effect: a right-click with a wand carrying it disassembles the AE2 block or
 * cable part being looked at, exactly as a quartz wrench would, and spends vis doing it.
 *
 * <p>There is no medium, so the cast is a bare root effect - the shape {@code FocusEffectBreak} has - and
 * acts at once on what the caster looks at; a projectile would add a travel delay to an aimed tool. The
 * original's flat {@code ignis 10 + aer 10} no longer maps, because a focus element carries one aspect
 * rather than a list.
 */
public final class FocusEffectAEWrench implements FocusEffect {

    /** Also the id the {@code FocusElementType} is registered under. */
    public static final ResourceLocation KEY = ThEIds.id("aewrench");

    /** {@code complexity / 5} is the vis price, so this is 2. */
    private static final int COMPLEXITY = 10;

    /** How far the focus looks for something to wrench. Matches a wand's casting reach. */
    private static final double REACH = 24.0;

    @Override
    public ResourceLocation id() {
        return KEY;
    }

    @Override
    public ResourceKey<IAspect> aspect() {
        // potentia is Thaumcraft's Energy aspect under its modern name, as the original focus was tinted.
        return TCAspects.POTENTIA;
    }

    @Override
    public int complexity(FocusSettings settings) {
        // No settings: there is nothing to tune about "use a wrench".
        return COMPLEXITY;
    }

    @Override
    public java.util.Set<FocusElement.SupplyType> requires() {
        return FocusElement.SUPPLIES_NOTHING;
    }

    @Override
    public java.util.Set<FocusElement.SupplyType> supplies() {
        return FocusElement.SUPPLIES_NOTHING;
    }

    @Override
    public boolean apply(CastContext ctx, FocusSettings settings, HitResult hit, Trajectory trajectory, int index) {
        Level level = ctx.level();
        if (!(level instanceof ServerLevel)) {
            // The client runs this too; the wrench action is server-authoritative, so the client half is a
            // no-op rather than a second attempt that would fight the server's.
            return false;
        }
        if (!(ctx.caster() instanceof Player player)) {
            return false;
        }

        // The target is looked up here rather than taken from the cast: this focus has no medium, so
        // relying on the engine's hit means relying on a root medium to have produced one, and when it has
        // not, apply() is never called - the cast does nothing at all, with no error. That was the "the AE
        // wrench focus does nothing" report. An engine-supplied block hit is still used.
        BlockHitResult target = hit instanceof BlockHitResult blockHit ? blockHit : rayTrace(player, level);
        if (target == null) {
            return false;
        }

        // The vis is settled here, not by the wand: the wand's charge runs before the cast and so cannot
        // tell a wrench that will happen from one that will not (see ItemFocusAEWrench.getVisCost). Asked
        // for first, committed only after AE2 has acted, so a caster who cannot pay gets nothing for free.
        float cost = ItemFocusAEWrench.visCost();
        if (!pay(player, cost, false)) {
            TCActionBar.sendPurple(player, "tc.wand.notenoughvis");
            return false;
        }

        // The main hand, whichever hand the cast came from. AE2's WrenchHook acts on the main hand alone, so
        // the borrowed wrench has to go where AE2 looks. Whatever the main hand was holding is put back by
        // AEWrench.use.
        if (!AEWrench.use(player, level, InteractionHand.MAIN_HAND, target)) {
            return false;
        }
        pay(player, cost, true);

        effect(level, player, Vec3.atCenterOf(target.getBlockPos()));
        return true;
    }

    /**
     * Charges the wand that is casting, the hand holding a wand with this focus, so the vis comes from the
     * wand the player used - as the wand's own charge did. A false {@code commit} only asks the price.
     */
    private static boolean pay(Player player, float cost, boolean commit) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof ItemWand wand
                    && wand.getFocusStack(stack).is(ModItems.FOCUS_AEWRENCH.get())) {
                return wand.consumeVis(stack, player, cost, false, !commit);
            }
        }
        return false;
    }

    /**
     * What the caster is looking at, out to a wand's reach. {@code ClipContext.Block.OUTLINE} rather than
     * {@code COLLIDER}: cable parts are not full collision shapes.
     */
    private static @Nullable BlockHitResult rayTrace(Player player, Level level) {
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 end = eye.add(player.getLookAngle().scale(REACH));
        BlockHitResult result =
                level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return result.getType() == HitResult.Type.BLOCK ? result : null;
    }

    @Override
    public void impactParticles(Level level, Vec3 pos, Vec3 look) {
        // Nothing: this effect has no projectile, so there is no impact separate from apply()'s.
    }

    /**
     * A beam from the wand to what it just took apart, plus a clunk; without it the block simply vanishes
     * and the player cannot tell a cast that worked from a click that did nothing. Sent per player rather
     * than to the level, so the beam is private to the caster.
     */
    private static void effect(Level level, Player player, Vec3 target) {
        level.playSound(null, BlockPos.containing(target), SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.PLAYERS,
                0.5F, 1.4F);

        if (!(level instanceof ServerLevel server)) {
            return;
        }
        var caster = player instanceof ServerPlayer sp ? sp : null;
        if (caster == null) {
            return;
        }

        Vec3 start = player.getEyePosition(1.0F);
        Vec3 delta = target.subtract(start);
        double distance = delta.length();
        if (distance < 0.5) {
            return;
        }
        // One particle per half block, so the spacing is the same however far away the target is.
        Vec3 step = delta.normalize().scale(0.5);
        Vec3 at = start.add(step);
        for (int i = 0, steps = (int) (distance / 0.5); i < steps; i++, at = at.add(step)) {
            server.sendParticles(caster, ParticleTypes.END_ROD, true, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }


}
