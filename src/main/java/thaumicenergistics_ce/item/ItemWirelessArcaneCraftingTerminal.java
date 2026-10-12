package thaumicenergistics_ce.item;

import appeng.api.ids.AEComponents;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.parts.PartHelper;
import appeng.helpers.WirelessTerminalMenuHost;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.menu.locator.ItemMenuHostLocator;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.recipe.arcane.ArcaneTerminalLink;
import thaumicenergistics_ce.registry.TECEMenuTypes;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;

public class ItemWirelessArcaneCraftingTerminal extends WirelessTerminalItem implements ArcaneTerminalLink {

    public static final double POWER_CAPACITY = 200_000;

    private static final String NBT_DIMENSION = "ArcaneDimension";

    private static final String NBT_POS = "ArcanePos";

    private static final String NBT_SIDE = "ArcaneSide";

    public ItemWirelessArcaneCraftingTerminal(DoubleSupplier powerCapacity, Item.Properties properties) {
        super(powerCapacity, properties);
    }

    @Override
    public appeng.api.upgrades.IUpgradeInventory getUpgrades(net.minecraft.world.item.ItemStack stack) {
        return QuantumTerminalSupport.upgrades(stack, (changed, upgrades) ->
                setAEMaxPowerMultiplier(changed, 1 + appeng.api.upgrades.Upgrades.getEnergyCardMultiplier(upgrades)));
    }

    @Override
    public boolean doesSneakBypassUse(ItemStack stack, LevelReader level, BlockPos pos, Player player) {
        return holdsArcaneTerminal(level, pos);
    }

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

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, net.minecraft.world.item.context.UseOnContext context) {
        var player = context.getPlayer();
        if (player != null && player.isSecondaryUseActive()
                && context.getLevel().getBlockEntity(context.getClickedPos()) instanceof IPartHost host) {
            var selected = host.selectPartWorld(context.getClickLocation());
            if (selected.part instanceof PartArcaneCraftingTerminal part) {
                if (!context.getLevel().isClientSide()) {
                    pairWith(stack, context.getLevel(), context.getClickedPos(), part.getSide());
                    var pos = context.getClickedPos();
                    player.sendOverlayMessage(Component.translatable(
                            "item.thaumicenergistics_ce.wireless_arcane_crafting_terminal.paired",
                            pos.getX(), pos.getY(), pos.getZ()));
                }
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        return super.use(level, player, hand);
    }

    @Override
    public MenuType<?> getMenuType() {
        return TECEMenuTypes.WIRELESS_ARCANE_CRAFTING_TERMINAL.get();
    }

    @Override
    public @Nullable WirelessTerminalMenuHost<?> getMenuHost(
            Player player, ItemMenuHostLocator locator, @Nullable BlockHitResult hitResult) {
        return new WirelessArcaneCraftingTerminalMenuHost(
                this, player, locator, (p, subMenu) -> this.openFromInventory(p, locator, true));
    }

    @Override
    public void pairWith(ItemStack terminal, Level level, BlockPos pos, Direction side) {
        CompoundTag tag = bindingTag(terminal);
        CompoundTag updated = tag == null ? new CompoundTag() : tag.copy();
        updated.putString(NBT_DIMENSION, level.dimension().identifier().toString());
        updated.putLong(NBT_POS, pos.asLong());
        updated.putString(NBT_SIDE, side.getName());
        terminal.set(DataComponents.CUSTOM_DATA, CustomData.of(updated));
    }

    public static @Nullable PartArcaneCraftingTerminal pairedTerminal(Level level, ItemStack terminal) {
        return pairedTerminal(level, terminal, false);
    }

    public static @Nullable PartArcaneCraftingTerminal pairedTerminal(Level level, ItemStack terminal, boolean crossDimension) {
        CompoundTag tag = bindingTag(terminal);
        if (tag == null || !tag.contains(NBT_POS)) {
            return null;
        }
        if (!level.dimension().identifier().toString().equals(tag.getStringOr(NBT_DIMENSION, ""))) {
            var dimension = net.minecraft.resources.Identifier.tryParse(tag.getStringOr(NBT_DIMENSION, ""));
            if (!crossDimension || !(level instanceof net.minecraft.server.level.ServerLevel server) || dimension == null) {
                return null;
            }
            level = server.getServer().getLevel(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION, dimension));
            if (level == null) return null;
        }
        Direction side = Direction.byName(tag.getStringOr(NBT_SIDE, ""));
        BlockPos pos = BlockPos.of(tag.getLongOr(NBT_POS, 0L));
        if (side == null || !level.isLoaded(pos)) {
            return null;
        }
        IPart part = PartHelper.getPart(level, pos, side);
        return part instanceof PartArcaneCraftingTerminal placed ? placed : null;
    }

    public static boolean unbind(ItemStack terminal) {
        boolean cleared = clearPairing(terminal);
        if (isLinked(terminal)) {
            WirelessTerminalItem.LINKABLE_HANDLER.unlink(terminal);
            cleared = true;
        }
        return cleared;
    }

    private static boolean clearPairing(ItemStack terminal) {
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

    private static boolean isLinked(ItemStack terminal) {
        return terminal.get(AEComponents.WIRELESS_LINK_TARGET) != null;
    }

    private static @Nullable CompoundTag bindingTag(ItemStack terminal) {
        CustomData data = terminal.get(DataComponents.CUSTOM_DATA);
        return data == null ? null : data.copyTag();
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable TooltipContext context,
            TooltipDisplay display,
            Consumer<Component> tooltip,
            TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        CompoundTag tag = bindingTag(stack);
        if (tag != null && tag.contains(NBT_POS)) {
            BlockPos pos = BlockPos.of(tag.getLongOr(NBT_POS, 0L));
            tooltip.accept(Component.translatable(
                    "tooltip.thaumicenergistics_ce.wireless_arcane_crafting_terminal.paired",
                    pos.getX(), pos.getY(), pos.getZ()));
        }
    }
}
