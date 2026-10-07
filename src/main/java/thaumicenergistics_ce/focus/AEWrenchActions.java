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
 * 潜行并左键点击携带 AE 扳手焦点的法杖，会把所看之物转动一步。
 * 两侧都运行它，且只有当 {@link #wouldTurn} 说转动会真的发生时
 * 才拿走挖掘，所以无法转动的方块或部件保留其正常挖掘。主手持有挖掘工具时
 * 绝不会被拿走挖掘，因为使用焦点时法杖就位于主手。AE2 用一个左键点击
 * 数据包回应部件点击，所以本事件会发布两次；{@code running} 把第二遍
 * 挡在外面，且只有某物真的转动了才扣一次转动的费用。
 */
@EventBusSubscriber(modid = ThEIds.MODID)
public final class AEWrenchActions {

    /** 设置 {@code -Dthaumicenergistics.aewrench.debug=true} 可记录一次左键点击为何算或不算转动。 */
    private static final boolean DEBUG = Boolean.getBoolean("thaumicenergistics.aewrench.debug");

    /** {@link #debug} 是否会输出；事件处理器在拼装日志行之前先询问。 */
    private static boolean debugEnabled() {
        return DEBUG;
    }

    private static void debug(String message, Object... args) {
        if (DEBUG) {
            ThELog.LOG.info("[aewrench] " + message, args);
        }
    }

    /** 没有任何条件匹配：说明是哪一条，这样才能把失效的手势与瞄错的手势区分开。 */
    private static boolean reject(String reason) {
        debug("no match: {}", reason);
        return false;
    }

    /** 与 {@link FocusEffectAEWrench} 中法杖右键相同的触及距离：工具是瞄准的，不是投掷的。 */
    private static final double REACH = 24.0;

    /** 在一次转动进行期间置位；关于取消与左键点击数据包见类注释。 */
    private static boolean running;

    private AEWrenchActions() {}

    /**
     * 这次左键点击到底是不是该手势：潜行、一只手可以放弃它的左键点击、
     * 携带该焦点的法杖，以及命中的方块就是所指定的方块。服务端与客户端都运行它。
     */
    public static boolean matches(Player player, Level level, BlockPos pos) {
        if (!player.isSecondaryUseActive()) {
            // 不是该手势：没有潜行时，无论手里拿着什么，这都是普通的左键点击。
            return reject("not sneaking");
        }
        ItemStack main = player.getMainHandItem();
        if (!main.isEmpty() && !holdsFocus(main)) {
            // 手里拿着镐或剑意味着挖掘，绝不转动：主手必须空着，
            // 或者就是法杖本身，因为焦点就是这样使用的；副手也可以。
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
            // 服务端从事件中只拿到方块，所以由玩家自己的射线检测决定真正
            // 瞄准的是哪个方块；不一致就表示「不是这个」，而不是去猜。
            return reject("cursor is on " + lookedAt.getBlockPos() + " but the event names " + pos);
        }
        debug("matched at {}", pos);
        return true;
    }

    /** 该物品堆是否是携带扳手焦点的法杖。 */
    public static boolean holdsFocus(ItemStack stack) {
        return TcWand.holdsFocus(stack, ModItems.FOCUS_AEWRENCH.get());
    }

    /** 玩家所看之物，最远到 {@link #REACH}，若一路无物阻挡则为 null。 */
    public static @Nullable BlockHitResult lookedAt(Player player, Level level) {
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 end = eye.add(player.getLookAngle().scale(REACH));
        // 用 OUTLINE 而不是 COLLIDER：线缆部件不是完整的碰撞形状。
        BlockHitResult hit =
                level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }

    /** 持有携带扳手焦点之法杖的那只手，两只手都没有时为 null。 */
    public static @Nullable ItemStack findWand(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (holdsFocus(stack)) {
                return stack;
            }
        }
        return null;
    }

    /** 对那把法杖扣费；{@code commit} 为 false 时只询问它是否付得起。 */
    private static boolean pay(ItemStack wand, Player player, float cost, boolean commit) {
        return commit ? TcWand.payVis(wand, player, cost) : TcWand.canPayVis(wand, player, cost);
    }

    /**
     * 把玩家所看之物转动一步并为其付费。仅服务端；除非真的转动了
     * 否则不扣费，而线缆交汇处是一个 {@link IPartHost}，不是方块。
     */
    public static boolean operate(Player player, Level level, BlockHitResult hit) {
        if (level.isClientSide() || player.isSpectator()) {
            // 客户端侧的转动会与服务端的相争；观察者的转动只会是个幽灵。
            return false;
        }
        if (running) {
            // AE2 的部件左键点击数据包会把本事件第二次发布；转动已经在进行了。
            return false;
        }

        ItemStack wand = findWand(player);
        if (wand == null) {
            return false;
        }

        BlockPos pos = hit.getBlockPos();
        float cost = ItemFocusAEWrench.visCost();
        if (!pay(wand, player, cost, false)) {
            // 只询问而不扣费：付不起的转动根本不能发生。
            TcActionBar.sendPurple(player, "tc.wand.notenoughvis");
            return false;
        }
        if (!level.mayInteract(player, pos) || !player.mayBuild()) {
            // 出生点保护，或不允许建造的游戏模式。AE2 自身的扳手路径在写入前也问
            // 同一个问题，而转动方块就是一次写入。
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
     * 该手势是否真会转动某物，给出答案时不写入任何东西。两侧都运行它，
     * 所以转不动任何东西的点击仍按正常挖掘处理，且两侧对此判断一致。
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
            // 只询问而不扣费：付不起的转动根本不能发生，这次点击
            // 仍然是一次点击，而不会白白被拿走。
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
     * AE2 朝向策略对该方块的答案，它无话可说时为 null。把
     * {@link BlockOrientation#rotateClockwiseAround} 绕被点击的那个面套用一遍。
     */
    private static @Nullable BlockState oriented(Level level, BlockPos pos, BlockState state, Direction face) {
        IOrientationStrategy strategy = IOrientationStrategy.get(state);
        if (!strategy.allowsPlayerRotation()) {
            // AE2 说对这个方块用扳手不是玩家的行为；接下来走属性回退。
            return null;
        }
        BlockOrientation orientation = BlockOrientation.get(strategy, state).rotateClockwiseAround(face);
        BlockState next = strategy.setOrientation(state, orientation.getSide(RelativeSide.FRONT), orientation.getSpin());
        // AE2 没有策略的方块在这里总是产出同一个状态，所以它永远不会胜出。
        return next != state && next.canSurvive(level, pos) ? next : null;
    }

    /**
     * 把方块转动一步：先用 AE2 自身的 {@link IOrientationStrategy}，再用它
     * 具有的任何 facing 属性。除非新状态不同且仍能在原地立足，否则不写入。
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
     * 该方块将会变成的状态，没有可转的目标时为 null。只读，正因如此
     * {@link #wouldTurn} 才能在不写入任何东西的情况下回答客户端所需的同一个问题。
     */
    private static @Nullable BlockState turned(Level level, BlockPos pos, Direction clickedFace) {
        BlockState state = level.getBlockState(pos);

        BlockState byStrategy = oriented(level, pos, state, clickedFace);
        if (byStrategy != null) {
            return byStrategy;
        }

        DirectionProperty property = pickProperty(state);
        if (property == null) {
            // 完全没有 facing 属性：这个方块没什么可转的，所以仍按正常方式挖掘。
            return null;
        }

        Direction current = state.getValue(property);
        for (Direction candidate : orderedValues(property, current)) {
            BlockState next = state.setValue(property, candidate);
            if (next != state && next.canSurvive(level, pos)) {
                return next;
            }
        }
        // 每个候选都会脱落：写入任一个都会让建筑变形，所以什么都不写。
        return null;
    }

    /** 命中点所在的方块内坐标，以方块自身的坐标系表示。 */
    private static Vec3 localPosOf(BlockHitResult hit, BlockPos pos) {
        return hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
    }

    /** 要循环的 facing 属性：完整的六向属性、水平属性，否则按名称取第一个。 */
    private static @Nullable DirectionProperty pickProperty(BlockState state) {
        if (state.hasProperty(BlockStateProperties.FACING)) {
            return BlockStateProperties.FACING;
        }
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return BlockStateProperties.HORIZONTAL_FACING;
        }
        // 按名称而不是按遍历顺序，这样带两个 facing 属性的方块
        // 总是转动同一个。
        return state.getProperties().stream()
                .filter(DirectionProperty.class::isInstance)
                .map(DirectionProperty.class::cast)
                .min(Comparator.comparing(facing -> facing.getName()))
                .orElse(null);
    }

    /**
     * 要尝试的取值，从当前值起按顺时针排列，这样每个方块转起来观感一致。
     * 没有罗盘环的属性（例如漏斗的）保留其声明顺序，只去掉当前值。
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
            // 不是罗盘属性：把声明顺序原样交回，而不是自己编一个旋转。
            List<Direction> declared = new ArrayList<>(values);
            declared.remove(current);
            return declared;
        }
        return ring;
    }

    /** 法杖右键本就会给出的音效和 END_ROD 光束：让方块可见地转过去。 */
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
        // 每半格一个粒子，所以无论目标多远，间距都一致。
        Vec3 step = delta.normalize().scale(0.5);
        Vec3 at = start.add(step);
        for (int i = 0, steps = (int) (distance / 0.5); i < steps; i++, at = at.add(step)) {
            server.sendParticles(caster, ParticleTypes.END_ROD, true, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /**
     * 手势到达。两侧都运行它并先询问 {@link #wouldTurn}，所以客户端只为
     * 真会转动物体的点击取消自己的挖掘；只有服务端写入。
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
            // 按住按钮期间客户端会持续发布本事件；只有起始那一次才是该手势。
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
            // matches() 已经检查过这一点；再查一遍让转动对究竟是哪个方块保持诚实。
            return;
        }
        if (!wouldTurn(player, level, hit)) {
            // 什么都转不动，所以不动这次点击，方块照常被挖掘。
            return;
        }

        if (level.isClientSide()) {
            // 客户端只拿走挖掘；在这里转动会与服务端重复转动一次。
            event.setCanceled(true);
            return;
        }
        if (operate(player, level, hit)) {
            event.setCanceled(true);
        }
    }

}
