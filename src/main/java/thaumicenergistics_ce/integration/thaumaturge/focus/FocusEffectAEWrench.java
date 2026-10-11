package thaumicenergistics_ce.integration.thaumaturge.focus;

import com.leclowndu93150.thaumaturge.TTIds;
import com.leclowndu93150.thaumaturge.api.spell.behavior.AbstractEffectBehavior;
import com.leclowndu93150.thaumaturge.api.spell.behavior.SpellBehaviorType;
import com.leclowndu93150.thaumaturge.api.spell.cast.CastContext;
import com.leclowndu93150.thaumaturge.api.spell.cast.SpellTarget;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.compat.thaumaturge.TcActionBar;
import thaumicenergistics_ce.compat.thaumaturge.TcWand;
import thaumicenergistics_ce.registry.ModItems;
import thaumicenergistics_ce.item.ItemFocusAEWrench;

public final class FocusEffectAEWrench extends AbstractEffectBehavior {

    public static final Identifier KEY = TTIds.rl("aewrench");

    public static final MapCodec<FocusEffectAEWrench> CODEC = MapCodec.unit(new FocusEffectAEWrench());

    private static final double REACH = 24.0;

    @Override
    public SpellBehaviorType<?> type() {
        return FocusElements.AEWRENCH_BEHAVIOR.get();
    }

    @Override
    protected boolean widens() {
        return false;
    }

    @Override
    protected void apply(CastContext ctx, SpellTarget target, float power, int index) {
        if (!(ctx.caster() instanceof Player player)) {
            return;
        }

        BlockHitResult hit = target.block().orElse(null);
        if (hit == null) {
            hit = rayTrace(player, ctx.level());
        }
        if (hit == null) {
            return;
        }


        if (!AEWrench.use(player, ctx.level(), InteractionHand.MAIN_HAND, hit)) {
            return;
        }

        effect(ctx.level(), player, Vec3.atCenterOf(hit.getBlockPos()));
    }

    private static @Nullable BlockHitResult rayTrace(Player player, ServerLevel level) {
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 end = eye.add(player.getLookAngle().scale(REACH));
        BlockHitResult result =
                level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return result.getType() == HitResult.Type.BLOCK ? result : null;
    }

    private static void effect(ServerLevel level, Player player, Vec3 target) {
        level.playSound(null, BlockPos.containing(target), SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.PLAYERS,
                0.5F, 1.4F);

        if (!(player instanceof ServerPlayer caster)) {
            return;
        }

        Vec3 start = player.getEyePosition(1.0F);
        Vec3 delta = target.subtract(start);
        double distance = delta.length();
        if (distance < 0.5) {
            return;
        }
        Vec3 step = delta.normalize().scale(0.5);
        Vec3 at = start.add(step);
        for (int i = 0, steps = (int) (distance / 0.5); i < steps; i++, at = at.add(step)) {
            level.sendParticles(caster, ParticleTypes.END_ROD, true, true, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

}
