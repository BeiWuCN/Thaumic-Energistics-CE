package thaumicenergistics_ce.item;

import appeng.api.ids.AEComponents;
import appeng.helpers.WirelessTerminalMenuHost;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.menu.locator.ItemMenuHostLocator;
import java.util.function.DoubleSupplier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.registry.TECEMenuTypes;

public class ItemWirelessArcaneCraftingTerminal extends WirelessTerminalItem {

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
    public void appendHoverText(ItemStack stack, TooltipContext context,
            net.minecraft.world.item.component.TooltipDisplay display,
            java.util.function.Consumer<net.minecraft.network.chat.Component> lines,
            net.minecraft.world.item.TooltipFlag flag) {
        super.appendHoverText(stack, context, display, QuantumTerminalSupport.connectionTooltip(stack, lines), flag);
    }

    @Override
    public @Nullable WirelessTerminalMenuHost<?> getMenuHost(
            Player player, ItemMenuHostLocator locator, @Nullable BlockHitResult hitResult) {
        return new WirelessArcaneCraftingTerminalMenuHost(
                this, player, locator, (p, subMenu) -> this.openFromInventory(p, locator, true));
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

}
