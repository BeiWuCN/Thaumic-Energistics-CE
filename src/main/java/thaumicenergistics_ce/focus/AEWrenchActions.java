package thaumicenergistics_ce.focus;

import appeng.api.orientation.BlockOrientation;
import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.RelativeSide;
import appeng.api.parts.IPartHost;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.compat.thaumaturge.TcActionBar;
import thaumicenergistics_ce.compat.thaumaturge.TcWand;
import thaumicenergistics_ce.init.ModItems;
import thaumicenergistics_ce.item.ItemFocusAEWrench;
import thaumicenergistics_ce.util.ThELog;

/**
 * 潜行左键点一下，把看的东西转一格；只有 {@link #wouldTurn} 说真转得动才不碰挖掘。
 * AE2 用左键包回应部件点击，事件触发两次，{@code running} 挡掉第二遍，真转了才扣一次费。
 */
@EventBusSubscriber(modid = ThEIds.MODID)
public final class AEWrenchActions {

    /** 设置 {@code -Dthaumicenergistics.aewrench.debug=true} 可记录左键点击为何算或不算转动。 */
    private static final boolean DEBUG = Boolean.getBoolean("thaumicenergistics.aewrench.debug");

    private static boolean debugEnabled() {
        return DEBUG;
    }

    private static void debug(String message, Object... args) {
        if (DEBUG) {
            ThELog.LOG.info("[aewrench] " + message, args);
        }
    }

    /** reason 只进调试日志：分开失效的手势和瞄错的点击。 */
    private static boolean reject(String reason) {
        debug("no match: {}", reason);
        return false;
    }

    /** 与 {@link FocusEffectAEWrench} 中法杖右键相同的触及距离。 */
    private static final double REACH = 24.0;

    /** 转动进行期间置位，挡掉 AE2 左键包带来的第二遍事件。 */
    private static boolean running;

    private AEWrenchActions() {}

    /**
     * 四条同时成立才算该手势：潜行、这只手肯让出左键、手持带该焦点的法杖、命中方块与事件给出的一致。
     */
    public static boolean matches(Player player, Level level, BlockPos pos) {
        if (!player.isSecondaryUseActive()) {
            return reject("not sneaking");
        }
        ItemStack main = player.getMainHandItem();
        if (!main.isEmpty() && !holdsFocus(main)) {
            // 主手拿镐或剑就算挖掘，不转动；主手要空着或握着法杖本身，副手也行。
            return reject("main hand holds " + main.getItem());
        }
        if (findWand(player) == null) {
            return reject("no wand carrying the focus in either hand");
        }
        BlockHitResult lookedAt = lookedAt(player, level);
        if (lookedAt == null) {
            return reject("nothing in reach");
        }
        if (!lookedAt.getBlockPos().equals(pos)) {
            // 事件只给方块，真正瞄准哪个由玩家自己的射线检测决定；不一致就拒绝，不猜。
            return reject("cursor is on " + lookedAt.getBlockPos() + " but the event names " + pos);
        }
        debug("matched at {}", pos);
        return true;
    }

    public static boolean holdsFocus(ItemStack stack) {
        return TcWand.holdsFocus(stack, ModItems.FOCUS_AEWRENCH.get());
    }

    /** 看向的方块，最远 {@link #REACH}；一路没阻挡就是 null。 */
    public static @Nullable BlockHitResult lookedAt(Player player, Level level) {
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 end = eye.add(player.getLookAngle().scale(REACH));
        // 用 OUTLINE，线缆部件没有完整碰撞形状。
        BlockHitResult hit =
                level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }

    /** 拿着带该焦点法杖的那只手，两只手都没有就是 null。 */
    public static @Nullable ItemStack findWand(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (holdsFocus(stack)) {
                return stack;
            }
        }
        return null;
    }

    /** 从法杖扣 vis；{@code commit} 为 false 时只试付不扣。 */
    private static boolean pay(ItemStack wand, Player player, float cost, boolean commit) {
        return commit ? TcWand.payVis(wand, player, cost) : TcWand.canPayVis(wand, player, cost);
    }

    /**
     * 转动所看之物并付费。仅服务端，真转了才扣费。
     * 线缆交汇处是一个 {@link IPartHost}，不是方块。
     */
    public static boolean operate(Player player, Level level, BlockHitResult hit) {
        if (level.isClientSide() || player.isSpectator()) {
            // 客户端转了会和服务端打架，观察者那边只是幽灵。
            return false;
        }
        if (running) {
            // AE2 左键包把事件发了第二遍，此时转动已经在跑。
            return false;
        }

        ItemStack wand = findWand(player);
        if (wand == null) {
            return false;
        }

        BlockPos pos = hit.getBlockPos();
        float cost = ItemFocusAEWrench.visCost();
        if (!pay(wand, player, cost, false)) {
            // 只试付不扣：付不起的转动根本发生不了，这里只发提示。
            TcActionBar.sendPurple(player, "tc.wand.notenoughvis");
            return false;
        }
        if (!level.mayInteract(player, pos) || !player.mayBuild()) {
            // 出生点保护或禁止建造。AE2 自己的扳手写入前问同一个问题，转方块同样是一次写入。
            return false;
        }

        boolean changed;
        running = true;
        try {
            if (level.getBlockEntity(pos) instanceof IPartHost host) {
                changed = AEWrench.rotateSelectedPart(player, level, host, localPosOf(hit, pos));
                debug("turned part at {} -> {}", pos, changed);
            } else {
                changed = rotateBlock(level, pos, hit.getDirection());
                debug("turned block at {} -> {}", pos, changed);
            }
        } finally {
            running = false;
        }

        if (!changed) {
            return false;
        }
        pay(wand, player, cost, true);
        effect(level, player, Vec3.atCenterOf(pos));
        return true;
    }

    /**
     * 预判这次点击会不会真的转动，不改任何状态。
     * 两侧都跑，转不动的点击仍按普通挖掘处理。
     */
    public static boolean wouldTurn(Player player, Level level, BlockHitResult hit) {
        if (player.isSpectator()) {
            return false;
        }
        ItemStack wand = findWand(player);
        if (wand == null) {
            debug("wouldTurn: no wand");
            return false;
        }
        if (!pay(wand, player, ItemFocusAEWrench.visCost(), false)) {
            // 只试付不扣：付不起就仍算普通点击，不会被拿走。
            debug("wouldTurn: cannot pay {}", ItemFocusAEWrench.visCost());
            return false;
        }

        BlockPos pos = hit.getBlockPos();
        boolean yes;
        if (level.getBlockEntity(pos) instanceof IPartHost host) {
            yes = AEWrench.wouldRotatePart(host, localPosOf(hit, pos));
        } else {
            yes = turned(level, pos, hit.getDirection()) != null;
        }
        debug("wouldTurn at {} -> {}", pos, yes);
        return yes;
    }

    /**
     * AE2 朝向策略对这个方块的答案，没有就是 null。
     * 把 {@link BlockOrientation#rotateClockwiseAround} 套在被点击的那个面上。
     */
    private static @Nullable BlockState oriented(Level level, BlockPos pos, BlockState state, Direction face) {
        IOrientationStrategy strategy = IOrientationStrategy.get(state);
        if (!strategy.allowsPlayerRotation()) {
            // AE2 认为对这个方块用扳手不算玩家行为，接着走属性回退。
            return null;
        }
        BlockOrientation orientation = BlockOrientation.get(strategy, state).rotateClockwiseAround(face);
        BlockState next = strategy.setOrientation(state, orientation.getSide(RelativeSide.FRONT), orientation.getSpin());
        // 没有策略的方块在这里总是得到同一个状态，永远选不上。
        return next != state && next.canSurvive(level, pos) ? next : null;
    }

    /**
     * 转方块一格：先问 AE2 的 {@link IOrientationStrategy}，再走 facing 属性。
     * 新状态不同且还能在原地立足才写入。
     */
    public static boolean rotateBlock(Level level, BlockPos pos, Direction clickedFace) {
        BlockState next = turned(level, pos, clickedFace);
        if (next == null) {
            return false;
        }
        level.setBlockAndUpdate(pos, next);
        return true;
    }

    /**
     * 方块会变成的状态，没有可转目标就是 null。只读，{@link #wouldTurn} 靠它不写入也能回答客户端。
     */
    private static @Nullable BlockState turned(Level level, BlockPos pos, Direction clickedFace) {
        BlockState state = level.getBlockState(pos);

        BlockState byStrategy = oriented(level, pos, state, clickedFace);
        if (byStrategy != null) {
            return byStrategy;
        }

        DirectionProperty property = pickProperty(state);
        if (property == null) {
            // 没有 facing 属性，没得可转，按普通挖掘。
            return null;
        }

        Direction current = state.getValue(property);
        for (Direction candidate : orderedValues(property, current)) {
            BlockState next = state.setValue(property, candidate);
            if (next != state && next.canSurvive(level, pos)) {
                return next;
            }
        }
        // 每个候选状态都会脱落，写下去就是拆建筑，一个都不写。
        return null;
    }

    /** 命中点在方块局部坐标系里的位置。 */
    private static Vec3 localPosOf(BlockHitResult hit, BlockPos pos) {
        return hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
    }

    /** 要循环的 facing 属性：六向、水平，都没有就按名字取第一个。 */
    private static @Nullable DirectionProperty pickProperty(BlockState state) {
        if (state.hasProperty(BlockStateProperties.FACING)) {
            return BlockStateProperties.FACING;
        }
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return BlockStateProperties.HORIZONTAL_FACING;
        }
        // 按名字取，不按遍历顺序：带两个 facing 属性的方块每次转同一个。
        return state.getProperties().stream()
                .filter(DirectionProperty.class::isInstance)
                .map(DirectionProperty.class::cast)
                .min(Comparator.comparing(facing -> facing.getName()))
                .orElse(null);
    }

    /**
     * 要试的取值从当前值起顺时针排，转起来的观感一致。
     * 不是罗盘环的属性（漏斗的）保持声明顺序，只去掉当前值。
     */
    private static List<Direction> orderedValues(DirectionProperty prop, Direction current) {
        List<Direction> values = new ArrayList<>(prop.getPossibleValues());
        List<Direction> ring = new ArrayList<>();
        Direction candidate =
                values.contains(current) && current.getAxis().isHorizontal() ? current : Direction.NORTH;
        for (int i = 0; i < 4; i++) {
            candidate = candidate.getClockWise();
            if (values.contains(candidate) && !ring.contains(candidate)) {
                ring.add(candidate);
            }
        }
        if (ring.size() < 2) {
            // 不是罗盘属性，声明顺序原样交回，不自造旋转。
            List<Direction> declared = new ArrayList<>(values);
            declared.remove(current);
            return declared;
        }
        return ring;
    }

    /** 法杖右键本来就有的音效和 END_ROD 光束，让转动看得见。 */
    public static void effect(Level level, Player player, Vec3 target) {
        level.playSound(null, BlockPos.containing(target), SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.PLAYERS,
                0.5F, 1.4F);

        if (!(level instanceof ServerLevel server)) {
            return;
        }
        if (!(player instanceof ServerPlayer caster)) {
            return;
        }

        Vec3 start = player.getEyePosition(1.0F);
        Vec3 delta = target.subtract(start);
        double distance = delta.length();
        if (distance < 0.5) {
            return;
        }
        // 每半格一个粒子，目标多远间距都一样。
        Vec3 step = delta.normalize().scale(0.5);
        Vec3 at = start.add(step);
        for (int i = 0, steps = (int) (distance / 0.5); i < steps; i++, at = at.add(step)) {
            server.sendParticles(caster, ParticleTypes.END_ROD, true, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /**
     * 手势到了。两侧都跑，先问 {@link #wouldTurn}：客户端只对真能转的点击取消挖掘。
     * 写入只在服务端。
     */
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Player probe = event.getEntity();
        if (debugEnabled() && event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.CLIENT_HOLD) {
            debug("event: side={} action={} canceled={} pos={} sneak={} main={} off={}",
                    event.getLevel().isClientSide() ? "client" : "server", event.getAction(), event.isCanceled(),
                    event.getPos(), probe.isSecondaryUseActive(), probe.getMainHandItem().getItem(),
                    probe.getOffhandItem().getItem());
        }
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) {
            // 按住左键期间事件反复发布，只有起始那一次算手势。
            return;
        }
        if (event.isCanceled()) {
            return;
        }

        Player player = event.getEntity();
        Level level = event.getLevel();
        if (!matches(player, level, event.getPos())) {
            return;
        }

        BlockHitResult hit = lookedAt(player, level);
        if (hit == null || !hit.getBlockPos().equals(event.getPos())) {
            // matches() 已经查过，这里再查一次，保证转的是点中的那个方块。
            return;
        }
        if (!wouldTurn(player, level, hit)) {
            // 转不动就放过这次点击，方块照常挖。
            return;
        }

        if (level.isClientSide()) {
            // 客户端只取消挖掘，在这里转会和另一侧重复一次。
            event.setCanceled(true);
            return;
        }
        if (operate(player, level, hit)) {
            event.setCanceled(true);
        }
    }

}
