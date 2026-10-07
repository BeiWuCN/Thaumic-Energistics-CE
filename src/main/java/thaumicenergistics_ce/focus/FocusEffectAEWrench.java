package thaumicenergistics_ce.focus;

import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aspect.TCAspects;
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
import thaumicenergistics_ce.compat.thaumaturge.TcWand;
import thaumicenergistics_ce.init.ModItems;
import thaumicenergistics_ce.item.ItemFocusAEWrench;

/**
 * 把 AE2 的扳手做成一种核心效果：法杖右键拆解正被注视的 AE2 方块。
 * 没有媒介，因此立刻作用于施法者注视的目标；若改用弹射物，会让瞄准工具产生延迟。
 */
public final class FocusEffectAEWrench implements FocusEffect {

    /** 也是 {@code FocusElementType} 注册所用的 id。 */
    public static final ResourceLocation KEY = ThEIds.id("aewrench");

    /** {@code complexity / 5} 就是 vis 价格，所以这里是 2。 */
    private static final int COMPLEXITY = 10;

    private static final double REACH = 24.0;

    @Override
    public ResourceLocation id() {
        return KEY;
    }

    @Override
    public ResourceKey<IAspect> aspect() {
        // [potentia] 是 Thaumcraft 的 [Energy] 要素在现代名称下的写法，原本的核心就是按它着色的。
        return TCAspects.POTENTIA;
    }

    @Override
    public int complexity(FocusSettings settings) {
        // 无设置项："use a wrench" 本身没有什么可调的。
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
            // 客户端也会执行到这里；扳手动作以服务端为准，因此客户端这一半
            // 是空操作，而不是会与服务端相争的第二次尝试。
            return false;
        }
        if (!(ctx.caster() instanceof Player player)) {
            return false;
        }

        // 在这里查找目标，而不要依赖这次施法：该核心没有媒介，所以引擎未命中就意味着
        // [apply()] 根本不会被调用——也就是 "AE wrench focus does nothing" 那份报告。
        BlockHitResult target = hit instanceof BlockHitResult blockHit ? blockHit : rayTrace(player, level);
        if (target == null) {
            return false;
        }

        // vis 在这里结算，而不是由法杖结算：法杖的充能在施法之前运行，分辨不出
        // 哪次会真的用扳手（见 [ItemFocusAEWrench.getVisCost]）。在 AE2 之后再提交。
        float cost = ItemFocusAEWrench.visCost();
        if (!pay(player, cost, false)) {
            TcActionBar.sendPurple(player, "tc.wand.notenoughvis");
            return false;
        }

        // 无论这次施法出自哪只手，一律取主手：AE2 的 [WrenchHook] 只作用于主手。
        // 它当时拿着的东西由 [AEWrench.use] 放回。
        if (!AEWrench.use(player, level, InteractionHand.MAIN_HAND, target)) {
            return false;
        }
        pay(player, cost, true);

        effect(level, player, Vec3.atCenterOf(target.getBlockPos()));
        return true;
    }

    /**
     * 向施放此效果的法杖收费，也就是向持有带该核心的法杖的那只手收费，这样 vis 就来自玩家所用的
     * 法杖，与法杖自身的充能一致。{@code commit} 为 false 时只是询价。
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
     * 施法者注视的目标，范围到法杖的可及距离。用 {@code ClipContext.Block.OUTLINE} 而不是
     * {@code COLLIDER}：线缆部件不是完整的碰撞形状。
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
        // 什么都不做：该效果没有弹射物，因此不存在独立于 [apply()] 的命中效果。
    }

    /**
     * 从法杖射向它刚刚拆解之处的光束：没有它，方块就只是凭空消失。按玩家逐个发送，
     * 因此这道光束只有施法者本人看得到。
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
        // 每半格一个粒子，这样无论目标多远，间距都一致。
        Vec3 step = delta.normalize().scale(0.5);
        Vec3 at = start.add(step);
        for (int i = 0, steps = (int) (distance / 0.5); i < steps; i++, at = at.add(step)) {
            server.sendParticles(caster, ParticleTypes.END_ROD, true, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

}
