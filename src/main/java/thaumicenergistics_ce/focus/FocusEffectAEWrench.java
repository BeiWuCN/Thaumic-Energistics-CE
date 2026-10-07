package thaumicenergistics_ce.focus;

import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.casters.CastContext;
import com.leclowndu93150.thaumaturge.api.casters.FocusEffect;
import com.leclowndu93150.thaumaturge.api.casters.FocusElement;
import com.leclowndu93150.thaumaturge.api.casters.FocusSettings;
import com.leclowndu93150.thaumaturge.api.casters.Trajectory;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.compat.thaumaturge.TcActionBar;
import thaumicenergistics_ce.compat.thaumaturge.TcAspects;
import thaumicenergistics_ce.compat.thaumaturge.TcWand;
import thaumicenergistics_ce.init.ModItems;
import thaumicenergistics_ce.item.ItemFocusAEWrench;

/**
 * 把 AE2 的扳手做成一种核心效果：法杖右键拆解正被注视的 AE2 方块。
 * 没有媒介，立刻对注视目标生效。
 */
public final class FocusEffectAEWrench implements FocusEffect {

    /** 也是 {@code FocusElementType} 注册用的 id。 */
    public static final ResourceLocation KEY = ThEIds.id("aewrench");

    /** vis 价格是 {@code complexity / 5}，10 除以 5 得 2。 */
    private static final int COMPLEXITY = 10;

    private static final double REACH = 24.0;

    @Override
    public ResourceLocation id() {
        return KEY;
    }

    @Override
    public ResourceKey<IAspect> aspect() {
        // [potentia] 就是 Thaumcraft 的 [Energy] 要素，老版本核心按它着色。
        return TcAspects.POTENTIA;
    }

    @Override
    public int complexity(FocusSettings settings) {
        return COMPLEXITY;
    }

    @Override
    public Set<FocusElement.SupplyType> requires() {
        return FocusElement.SUPPLIES_NOTHING;
    }

    @Override
    public Set<FocusElement.SupplyType> supplies() {
        return FocusElement.SUPPLIES_NOTHING;
    }

    @Override
    public boolean apply(CastContext ctx, FocusSettings settings, HitResult hit, Trajectory trajectory, int index) {
        Level level = ctx.level();
        if (!(level instanceof ServerLevel)) {
            // 客户端也会走到这里；扳手动作以服务端为准，这一半返回 false 就行。
            return false;
        }
        if (!(ctx.caster() instanceof Player player)) {
            return false;
        }

        // 目标在这里查，别指望这次施法：核心没有媒介，引擎未命中时 [apply()] 不会被调用。
        // 那就是 “AE wrench focus does nothing” 报告的成因。
        BlockHitResult target = hit instanceof BlockHitResult blockHit ? blockHit : rayTrace(player, level);
        if (target == null) {
            return false;
        }

        // vis 在这里结账，不走法杖：法杖充能先于施法，分不清哪次真用扳手。
        // 见 [ItemFocusAEWrench.getVisCost]；AE2 之后再提交。
        float cost = ItemFocusAEWrench.visCost();
        if (!pay(player, cost, false)) {
            TcActionBar.sendPurple(player, "tc.wand.notenoughvis");
            return false;
        }

        // 一律取主手，AE2 的 [WrenchHook] 只看主手。
        // 主手当时拿着的东西由 [AEWrench.use] 放回。
        if (!AEWrench.use(player, level, InteractionHand.MAIN_HAND, target)) {
            return false;
        }
        pay(player, cost, true);

        effect(level, player, Vec3.atCenterOf(target.getBlockPos()));
        return true;
    }

    /**
     * 向拿着这根法杖的手收费，vis 才和法杖自身充能对得上。
     * {@code commit} 为 false 时只询价。
     */
    private static boolean pay(Player player, float cost, boolean commit) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (TcWand.holdsFocus(stack, ModItems.FOCUS_AEWRENCH.get())) {
                return commit ? TcWand.payVis(stack, player, cost) : TcWand.canPayVis(stack, player, cost);
            }
        }
        return false;
    }

    /**
     * 施法者注视的方块，到法杖可及距离为止。
     * {@code ClipContext.Block.OUTLINE} 才行：线缆部件不是完整的碰撞形状。
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
        // 空实现：没有弹射物，就没有独立于 [apply()] 的命中效果。
    }

    /**
     * 从法杖射向刚拆的位置；不加这道光束方块就是凭空消失。
     * 按玩家逐个发，只有施法者看得到。
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
        // 每半格一个粒子，目标再远间距也一致。
        Vec3 step = delta.normalize().scale(0.5);
        Vec3 at = start.add(step);
        for (int i = 0, steps = (int) (distance / 0.5); i < steps; i++, at = at.add(step)) {
            server.sendParticles(caster, ParticleTypes.END_ROD, true, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

}
