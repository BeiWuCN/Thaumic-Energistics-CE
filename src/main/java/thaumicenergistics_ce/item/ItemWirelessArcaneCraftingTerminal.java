package thaumicenergistics_ce.item;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.parts.PartHelper;
import appeng.helpers.WirelessTerminalMenuHost;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.menu.locator.ItemMenuHostLocator;
import java.util.List;
import java.util.function.DoubleSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.arcane.ArcaneTerminalLink;
import thaumicenergistics_ce.init.ModMenuTypes;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;

/**
 * The Wireless Arcane Crafting Terminal: a carried arcane workbench, showing the grid of the placed
 * terminal it is bound to.
 * <ul>
 *   <li>Sneaking on that terminal binds the two; a sneak left-click unbinds them - one state, two places.
 *   <li>Its vis comes from the aura around the player: a carried workbench has no block to drain.
 * </ul>
 */
public class ItemWirelessArcaneCraftingTerminal extends WirelessTerminalItem implements ArcaneTerminalLink {

    public static final double POWER_CAPACITY = 200_000;

    private static final String NBT_DIMENSION = "ArcaneDimension";

    private static final String NBT_POS = "ArcanePos";

    private static final String NBT_SIDE = "ArcaneSide";

    public ItemWirelessArcaneCraftingTerminal(DoubleSupplier powerCapacity, Item.Properties properties) {
        super(powerCapacity, properties);
    }

    /**
     * Only a block that already carries an Arcane Crafting Terminal is worth handing the sneak to: on any
     * other part the click would reach a machine the player was not aiming at.
     */
    @Override
    public boolean doesSneakBypassUse(ItemStack stack, LevelReader level, BlockPos pos, Player player) {
        return holdsArcaneTerminal(level, pos);
    }

    /**
     * True when that block carries the terminal on any face. The face itself is not in the signature, so
     * a cable holding one is opened up as a whole; the click still only pairs on the face that holds it.
     */
    private static boolean holdsArcaneTerminal(LevelReader level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof IPartHost host)) {
            return false;
        }
        if (host.getPart(null) instanceof PartArcaneCraftingTerminal) {
            return true;
        }
        for (Direction side : Direction.values()) {
            if (host.getPart(side) instanceof PartArcaneCraftingTerminal) {
                return true;
            }
        }
        return false;
    }

    /**
     * Pairing is a sneak gesture, so a sneak never also opens the screen: without this, one click would
     * bind the item and leave the player looking at a grid they did not ask for.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive()) {
            return InteractionResultHolder.pass(player.getItemInHand(hand));
        }
        return super.use(level, player, hand);
    }

    @Override
    public MenuType<?> getMenuType() {
        return ModMenuTypes.WIRELESS_ARCANE_CRAFTING_TERMINAL.get();
    }

    @Override
    public @Nullable WirelessTerminalMenuHost<?> getMenuHost(
            Player player, ItemMenuHostLocator locator, @Nullable BlockHitResult hitResult) {
        return new WirelessArcaneCraftingTerminalMenuHost(
                this, player, locator, (p, subMenu) -> this.openFromInventory(p, locator, true));
    }

    /**
     * Remembers one placed terminal on the item; the dimension is kept too, since two of them can hold
     * the same coordinates.
     */
    @Override
    public void pairWith(ItemStack terminal, Level level, BlockPos pos, Direction side) {
        CompoundTag tag = bindingTag(terminal);
        CompoundTag updated = tag == null ? new CompoundTag() : tag.copy();
        updated.putString(NBT_DIMENSION, level.dimension().location().toString());
        updated.putLong(NBT_POS, pos.asLong());
        updated.putString(NBT_SIDE, side.getName());
        terminal.set(DataComponents.CUSTOM_DATA, CustomData.of(updated));
    }

    /**
     * The placed terminal this item was paired with, or {@code null} when there is none, it stands in
     * another dimension, or its chunk is not loaded.
     */
    public static @Nullable PartArcaneCraftingTerminal pairedTerminal(Level level, ItemStack terminal) {
        CompoundTag tag = bindingTag(terminal);
        if (tag == null || !tag.contains(NBT_POS)) {
            return null;
        }
        if (!level.dimension().location().toString().equals(tag.getString(NBT_DIMENSION))) {
            return null;
        }
        Direction side = Direction.byName(tag.getString(NBT_SIDE));
        BlockPos pos = BlockPos.of(tag.getLong(NBT_POS));
        if (side == null || !level.isLoaded(pos)) {
            return null;
        }
        IPart part = PartHelper.getPart(level, pos, side);
        return part instanceof PartArcaneCraftingTerminal placed ? placed : null;
    }

    /**
     * Forgets the paired terminal. True only when there was one to forget, so that a second wipe is not
     * announced to the player as if it had done something.
     */
    public static boolean unbind(ItemStack terminal) {
        CompoundTag tag = bindingTag(terminal);
        if (tag == null || !tag.contains(NBT_POS)) {
            return false;
        }
        tag.remove(NBT_DIMENSION);
        tag.remove(NBT_POS);
        tag.remove(NBT_SIDE);
        if (tag.isEmpty()) {
            terminal.remove(DataComponents.CUSTOM_DATA);
        } else {
            terminal.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
        return true;
    }

    private static @Nullable CompoundTag bindingTag(ItemStack terminal) {
        CustomData data = terminal.get(DataComponents.CUSTOM_DATA);
        return data == null ? null : data.copyTag();
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        CompoundTag tag = bindingTag(stack);
        if (tag != null && tag.contains(NBT_POS)) {
            BlockPos pos = BlockPos.of(tag.getLong(NBT_POS));
            tooltip.add(Component.translatable(
                    "tooltip.thaumicenergistics_ce.wireless_arcane_crafting_terminal.paired",
                    pos.getX(), pos.getY(), pos.getZ()));
        }
    }
}
