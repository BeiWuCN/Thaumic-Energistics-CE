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
 * Sneak and left-click with a wand carrying the AE wrench focus turns what is looked at one step.
 * Both sides run it, and mining is taken away only when {@link #wouldTurn} says a turn would
 * really happen, so a block or part that cannot turn keeps its normal mining. A mining tool in the
 * main hand is never taken away from mining, because the wand lives there when its focus is used.
 * AE2 answers a part click with a left-click packet, so this event is posted twice; {@code running}
 * fences the second pass off, and a turn is charged only once something has really turned.
 */
@EventBusSubscriber(modid = ThEIds.MODID)
public final class AEWrenchActions {

    /** Set {@code -Dthaumicenergistics.aewrench.debug=true} to trace why a left-click did or did not turn. */
    private static final boolean DEBUG = Boolean.getBoolean("thaumicenergistics.aewrench.debug");

    /** Whether {@link #debug} will print; the event handler asks before building a line. */
    private static boolean debugEnabled() {
        return DEBUG;
    }

    private static void debug(String message, Object... args) {
        if (DEBUG) {
            ThELog.LOG.info("[aewrench] " + message, args);
        }
    }

    /** Nothing matched: say which clause, so a dead gesture can be told from a misaimed one. */
    private static boolean reject(String reason) {
        debug("no match: {}", reason);
        return false;
    }

    /** Same reach as the wand right-click in {@link FocusEffectAEWrench}: the tool is aimed, not thrown. */
    private static final double REACH = 24.0;

    /** Set while a turn is in flight; see the class comment on cancellation and the left-click packet. */
    private static boolean running;

    private AEWrenchActions() {}

    /**
     * Whether this left-click is the gesture at all: a sneak, a hand that may give up its left-click, a
     * wand carrying the focus, and the block hit being the block named. Server and client both run it.
     */
    public static boolean matches(Player player, Level level, BlockPos pos) {
        if (!player.isSecondaryUseActive()) {
            // Not the gesture: without the sneak this is an ordinary left-click, whatever the hand holds.
            return reject("not sneaking");
        }
        ItemStack main = player.getMainHandItem();
        if (!main.isEmpty() && !holdsFocus(main)) {
            // A pickaxe or a sword in hand means mining, never turning: the main hand must be free
            // or be the wand itself, since that is how its focus is used; the offhand works too.
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
            // The server gets only the block from the event, so the player's own raycast decides which
            // block was really aimed at; a mismatch means "not this one" rather than a guess.
            return reject("cursor is on " + lookedAt.getBlockPos() + " but the event names " + pos);
        }
        debug("matched at {}", pos);
        return true;
    }

    /** Whether this stack is a wand that is carrying the wrench focus. */
    public static boolean holdsFocus(ItemStack stack) {
        return TcWand.holdsFocus(stack, ModItems.FOCUS_AEWRENCH.get());
    }

    /** What the player is looking at, out to {@link #REACH}, or null if nothing blocks the way. */
    public static @Nullable BlockHitResult lookedAt(Player player, Level level) {
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 end = eye.add(player.getLookAngle().scale(REACH));
        // OUTLINE, not COLLIDER: cable parts are not full collision shapes.
        BlockHitResult hit =
                level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }

    /** The hand holding a wand that has the wrench focus, or null when neither does. */
    public static @Nullable ItemStack findWand(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (holdsFocus(stack)) {
                return stack;
            }
        }
        return null;
    }

    /** Charges that wand, or with {@code commit} false only asks whether it could pay. */
    private static boolean pay(ItemStack wand, Player player, float cost, boolean commit) {
        return commit ? TcWand.payVis(wand, player, cost) : TcWand.canPayVis(wand, player, cost);
    }

    /**
     * Turns what the player looked at one step and pays for it. Server only; nothing is charged
     * unless something really turned, and a cable junction is a {@link IPartHost}, not a block.
     */
    public static boolean operate(Player player, Level level, BlockHitResult hit) {
        if (level.isClientSide() || player.isSpectator()) {
            // A client-side turn would fight the server's; a spectator's would be a ghost.
            return false;
        }
        if (running) {
            // AE2's part-left-click packet posts this event a second time; the turn is already happening.
            return false;
        }

        ItemStack wand = findWand(player);
        if (wand == null) {
            return false;
        }

        BlockPos pos = hit.getBlockPos();
        float cost = ItemFocusAEWrench.visCost();
        if (!pay(wand, player, cost, false)) {
            // Asked, not charged: a turn that cannot be afforded must not happen at all.
            TcActionBar.sendPurple(player, "tc.wand.notenoughvis");
            return false;
        }
        if (!level.mayInteract(player, pos) || !player.mayBuild()) {
            // Spawn protection, or a gamemode that may not build. AE2's own wrench path asks the same
            // question before it writes, and turning a block is a write.
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
     * Whether the gesture would really turn something, answered without writing anything. Both
     * sides run it, so a click that turns nothing is still mined normally, and both agree on that.
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
            // Asked, not charged: a turn that cannot be afforded must not happen at all, and the click
            // stays a click rather than being taken away for nothing.
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
     * The AE2 orientation strategy's answer for this block, or null if it has nothing to say. Copies
     * {@link BlockOrientation#rotateClockwiseAround} around the face that was clicked.
     */
    private static @Nullable BlockState oriented(Level level, BlockPos pos, BlockState state, Direction face) {
        IOrientationStrategy strategy = IOrientationStrategy.get(state);
        if (!strategy.allowsPlayerRotation()) {
            // AE2 says wrenching this block is not something a player does; the property fallback is next.
            return null;
        }
        BlockOrientation orientation = BlockOrientation.get(strategy, state).rotateClockwiseAround(face);
        BlockState next = strategy.setOrientation(state, orientation.getSide(RelativeSide.FRONT), orientation.getSpin());
        // A block AE2 has no strategy for always yields the same state here, so it never wins.
        return next != state && next.canSurvive(level, pos) ? next : null;
    }

    /**
     * Turns a block one step: AE2's own {@link IOrientationStrategy} first, then any facing property it
     * has. Nothing is written unless the new state differs and still fits where it stands.
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
     * The state this block would take, or null if it has nothing to turn to. Read-only, which is what
     * lets {@link #wouldTurn} answer the same question the client needs without writing anything.
     */
    private static @Nullable BlockState turned(Level level, BlockPos pos, Direction clickedFace) {
        BlockState state = level.getBlockState(pos);

        BlockState byStrategy = oriented(level, pos, state, clickedFace);
        if (byStrategy != null) {
            return byStrategy;
        }

        DirectionProperty property = pickProperty(state);
        if (property == null) {
            // No facing property at all: this block has nothing to turn, so it stays mined normally.
            return null;
        }

        Direction current = state.getValue(property);
        for (Direction candidate : orderedValues(property, current)) {
            BlockState next = state.setValue(property, candidate);
            if (next != state && next.canSurvive(level, pos)) {
                return next;
            }
        }
        // Every candidate would fall off: writing one would deform the build, so nothing is written.
        return null;
    }

    /** The block position a hit is inside of, in the block's own frame. */
    private static Vec3 localPosOf(BlockHitResult hit, BlockPos pos) {
        return hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
    }

    /** The facing property to cycle: the full six-way one, the horizontal one, else the first by name. */
    private static @Nullable DirectionProperty pickProperty(BlockState state) {
        if (state.hasProperty(BlockStateProperties.FACING)) {
            return BlockStateProperties.FACING;
        }
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return BlockStateProperties.HORIZONTAL_FACING;
        }
        // By name rather than by iteration order, so a block with two facing properties always turns
        // the same one.
        return state.getProperties().stream()
                .filter(DirectionProperty.class::isInstance)
                .map(DirectionProperty.class::cast)
                .min(Comparator.comparing(facing -> facing.getName()))
                .orElse(null);
    }

    /**
     * The values to try, clockwise from the current one so the turn looks the same on every block.
     * A property with no compass ring, such as a hopper's, keeps its order minus the current value.
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
            // Not a compass property: hand the declared order back rather than invent a rotation.
            List<Direction> declared = new ArrayList<>(values);
            declared.remove(current);
            return declared;
        }
        return ring;
    }

    /** The sound and the END_ROD beam the wand right-click already gives: the block visibly turns. */
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
        // One particle per half block, so the spacing is the same however far away the target is.
        Vec3 step = delta.normalize().scale(0.5);
        Vec3 at = start.add(step);
        for (int i = 0, steps = (int) (distance / 0.5); i < steps; i++, at = at.add(step)) {
            server.sendParticles(caster, ParticleTypes.END_ROD, true, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /**
     * The gesture arrived. Both sides run it and ask {@link #wouldTurn} first, so the client only
     * cancels its own mining for a click that really turns something; the server alone writes.
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
            // The client posts this while the button is held down; only the start is the gesture.
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
            // matches() already checked this; rechecking keeps the turn honest about which block.
            return;
        }
        if (!wouldTurn(player, level, hit)) {
            // Nothing would turn, so the click is left alone and the block is mined as usual.
            return;
        }

        if (level.isClientSide()) {
            // The client only takes the mining away; turning here would double-turn against the server.
            event.setCanceled(true);
            return;
        }
        if (operate(player, level, hit)) {
            event.setCanceled(true);
        }
    }

}
