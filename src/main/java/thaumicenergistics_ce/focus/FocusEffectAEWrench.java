package thaumicenergistics_ce.focus;

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
import thaumicenergistics_ce.init.ModItems;
import thaumicenergistics_ce.item.ItemFocusAEWrench;

/**
 * 把 AE2 的扳手做成一种核心效果：法杖右键拆解正被注视的 AE2 方块。
 * 没有媒介，立刻对注视目标生效。
 */
public final class FocusEffectAEWrench extends AbstractEffectBehavior {

    /**
     * 行为与法术部件共用的路径，注册进的是 Thaumaturge 的命名空间。
     * 这不是笔误：行为类型只能用 {@code TTSpellBehaviors.BEHAVIORS} 那个注册器加，
     * 而它建在 {@code thaumaturge} 命名空间下；数据包那边也只读
     * {@code data/thaumaturge/thaumaturge/spell_part/}。
     * 所以行为 id 与法术部件 id 都是 {@code thaumaturge:aewrench}，
     * 本 mod 自己的命名空间只出现在贴图路径与物品 id 上。
     */
    public static final Identifier KEY = TTIds.rl("aewrench");

    /** 「用扳手」没什么可调的，法术部件的 JSON 没有可读的选项。 */
    public static final MapCodec<FocusEffectAEWrench> CODEC = MapCodec.unit(new FocusEffectAEWrench());

    private static final double REACH = 24.0;

    @Override
    public SpellBehaviorType<?> type() {
        return FocusElements.AEWRENCH_BEHAVIOR.get();
    }

    @Override
    protected boolean widens() {
            // 客户端也会走到这里；扳手动作以服务端为准，这一半返回 false 就行。
        return false;
    }

    @Override
    protected void apply(CastContext ctx, SpellTarget target, float power, int index) {
        if (!(ctx.caster() instanceof Player player)) {
            return;
        }

        // 目标在这里查，别指望这次施法：这个效果前面没有投递，
        // 交给它的目标只有施法者，而施法者身上没有方块。
        BlockHitResult hit = target.block().orElse(null);
        if (hit == null) {
            hit = rayTrace(player, ctx.level());
        }
        if (hit == null) {
            return;
        }

        // 价格算法术自己的，法杖已经付过，这里没什么可收的；
        // vis 在施法之前就扣掉，所以空放一发照样花钱。

        // 一律取主手，不管这次施法从哪只手来：AE2 的 WrenchHook 只看主手。
        // 主手当时拿着的东西由 AEWrench.use 放回。
        if (!AEWrench.use(player, ctx.level(), InteractionHand.MAIN_HAND, hit)) {
            return;
        }

        effect(ctx.level(), player, Vec3.atCenterOf(hit.getBlockPos()));
    }

    /**
     * 施法者注视的方块，到法杖可及距离为止。用 {@code ClipContext.Block.OUTLINE}，不是
     * {@code COLLIDER}：线缆部件不是完整的碰撞形状。
     */
    private static @Nullable BlockHitResult rayTrace(Player player, ServerLevel level) {
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 end = eye.add(player.getLookAngle().scale(REACH));
        BlockHitResult result =
                level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return result.getType() == HitResult.Type.BLOCK ? result : null;
    }

    /**
     * 从法杖射向刚拆掉的位置；不加这道光束方块就是凭空消失。
     * 按玩家逐个发，只有施法者看得到。
     */
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
        // 每半格一个粒子，目标再远间距也一致。
        Vec3 step = delta.normalize().scale(0.5);
        Vec3 at = start.add(step);
        for (int i = 0, steps = (int) (distance / 0.5); i < steps; i++, at = at.add(step)) {
            // 第二个标志是 alwaysShow：光束又短又细，干脆也发给旁观者，
            // 免得只留给碰巧站在命中点附近的人。
            level.sendParticles(caster, ParticleTypes.END_ROD, true, true, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

}
