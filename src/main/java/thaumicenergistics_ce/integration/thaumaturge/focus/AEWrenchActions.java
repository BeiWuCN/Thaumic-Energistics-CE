package thaumicenergistics_ce.integration.thaumaturge.focus;

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
import net.minecraft.world.level.block.state.properties.EnumProperty;
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
import thaumicenergistics_ce.registry.TECEItems;
import thaumicenergistics_ce.item.ItemFocusAEWrench;

@EventBusSubscriber(modid = ThEIds.MODID)
public final class AEWrenchActions {

    private static final double REACH = 24.0;

    private static boolean running;

    private AEWrenchActions() {}

    public static boolean matches(Player player, Level level, BlockPos pos) {
        if (!player.isSecondaryUseActive()) {
            return false;
        }
        ItemStack main = player.getMainHandItem();
        if (!main.isEmpty() && !holdsFocus(main)) {
            return false;
        }
        if (findWand(player) == null) {
            return false;
        }
        BlockHitResult lookedAt = lookedAt(player, level);
        if (lookedAt == null) {
            return false;
        }
        if (!lookedAt.getBlockPos().equals(pos)) {
            return false;
        }

        return true;
    }

    public static boolean holdsFocus(ItemStack stack) {
        return TcWand.holdsFocus(stack, TECEItems.FOCUS_AEWRENCH.get());
    }

    public static @Nullable BlockHitResult lookedAt(Player player, Level level) {
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 end = eye.add(player.getLookAngle().scale(REACH));
        BlockHitResult hit =
                level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }

    public static @Nullable ItemStack findWand(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (holdsFocus(stack)) {
                return stack;
            }
        }
        return null;
    }

    private static boolean pay(ItemStack wand, Player player, float cost, boolean commit) {
        return commit ? TcWand.payVis(wand, player, cost) : TcWand.canPayVis(wand, player, cost);
    }

    public static boolean operate(Player player, Level level, BlockHitResult hit) {
        if (level.isClientSide() || player.isSpectator()) {
            return false;
        }
        if (running) {
            return false;
        }

        ItemStack wand = findWand(player);
        if (wand == null) {
            return false;
        }

        BlockPos pos = hit.getBlockPos();
        float cost = ItemFocusAEWrench.visCost();
        if (!pay(wand, player, cost, false)) {
            TcActionBar.sendPurple(player, "tc.wand.notenoughvis");
            return false;
        }
        if (!level.mayInteract(player, pos) || !player.mayBuild()) {
            return false;
        }

        boolean changed;
        running = true;
        try {
            if (level.getBlockEntity(pos) instanceof IPartHost host) {
                changed = AEWrench.rotateSelectedPart(player, level, host, localPosOf(hit, pos));

            } else {
                changed = rotateBlock(level, pos, hit.getDirection());

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

    public static boolean wouldTurn(Player player, Level level, BlockHitResult hit) {
        if (player.isSpectator()) {
            return false;
        }
        ItemStack wand = findWand(player);
        if (wand == null) {

            return false;
        }
        if (!pay(wand, player, ItemFocusAEWrench.visCost(), false)) {

            return false;
        }

        BlockPos pos = hit.getBlockPos();
        boolean yes;
        if (level.getBlockEntity(pos) instanceof IPartHost host) {
            yes = AEWrench.wouldRotatePart(host, localPosOf(hit, pos));
        } else {
            yes = turned(level, pos, hit.getDirection()) != null;
        }

        return yes;
    }

    private static @Nullable BlockState oriented(Level level, BlockPos pos, BlockState state, Direction face) {
        IOrientationStrategy strategy = IOrientationStrategy.get(state);
        if (!strategy.allowsPlayerRotation()) {
            return null;
        }
        BlockOrientation orientation = BlockOrientation.get(strategy, state).rotateClockwiseAround(face);
        BlockState next = strategy.setOrientation(state, orientation.getSide(RelativeSide.FRONT), orientation.getSpin());
        return next != state && next.canSurvive(level, pos) ? next : null;
    }

    public static boolean rotateBlock(Level level, BlockPos pos, Direction clickedFace) {
        BlockState next = turned(level, pos, clickedFace);
        if (next == null) {
            return false;
        }
        level.setBlockAndUpdate(pos, next);
        return true;
    }

    private static @Nullable BlockState turned(Level level, BlockPos pos, Direction clickedFace) {
        BlockState state = level.getBlockState(pos);

        BlockState byStrategy = oriented(level, pos, state, clickedFace);
        if (byStrategy != null) {
            return byStrategy;
        }

        EnumProperty<Direction> property = pickProperty(state);
        if (property == null) {
            return null;
        }

        Direction current = state.getValue(property);
        for (Direction candidate : orderedValues(property, current)) {
            BlockState next = state.setValue(property, candidate);
            if (next != state && next.canSurvive(level, pos)) {
                return next;
            }
        }
        return null;
    }

    private static Vec3 localPosOf(BlockHitResult hit, BlockPos pos) {
        return hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
    }

    @SuppressWarnings("unchecked")
    private static @Nullable EnumProperty<Direction> pickProperty(BlockState state) {
        if (state.hasProperty(BlockStateProperties.FACING)) {
            return BlockStateProperties.FACING;
        }
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return BlockStateProperties.HORIZONTAL_FACING;
        }
        return state.getProperties().stream()
                .filter(prop -> prop instanceof EnumProperty<?> enumProp
                        && Direction.class.isAssignableFrom(enumProp.getValueClass()))
                .map(prop -> (EnumProperty<Direction>) prop)
                .min(Comparator.comparing(facing -> facing.getName(Direction.NORTH)))
                .orElse(null);
    }

    private static List<Direction> orderedValues(EnumProperty<Direction> prop, Direction current) {
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
            List<Direction> declared = new ArrayList<>(values);
            declared.remove(current);
            return declared;
        }
        return ring;
    }

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
        Vec3 step = delta.normalize().scale(0.5);
        Vec3 at = start.add(step);
        for (int i = 0, steps = (int) (distance / 0.5); i < steps; i++, at = at.add(step)) {
            server.sendParticles(caster, ParticleTypes.END_ROD, true, true, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Player probe = event.getEntity();

        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) {
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
            return;
        }
        if (!wouldTurn(player, level, hit)) {
            return;
        }

        if (level.isClientSide()) {
            event.setCanceled(true);
            return;
        }
        if (operate(player, level, hit)) {
            event.setCanceled(true);
        }
    }

}
