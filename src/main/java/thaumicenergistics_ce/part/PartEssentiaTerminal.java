package thaumicenergistics_ce.part;

import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import appeng.api.parts.IPartItem;
import appeng.api.util.KeyTypeSelection;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.parts.reporting.AbstractTerminalPart;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.phys.Vec3;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.init.ModMenuTypes;
import thaumicenergistics_ce.integration.ae2.AEssentiaKeyType;

/**
 * 作为线缆部件的源质终端：一台只列源质的 AE2 终端。
 * 过滤用 {@link KeyTypeSelection}，即 AE2 自己给终端键类型的通道；父类的选择允许所有类型，
 * 我们这份替换它，字段就由手工读写。父类的字段是私有的，它的读/写会把宽松选择存下来。
 */
public class PartEssentiaTerminal extends AbstractTerminalPart {

    public static final Identifier MODEL_BASE = ThEIds.id("parts/essentia_terminal_base");
    public static final Identifier MODEL_OFF = ThEIds.id("parts/essentia_terminal_off");
    public static final Identifier MODEL_ON = ThEIds.id("parts/essentia_terminal_on");
    public static final Identifier MODEL_HAS_CHANNEL = ThEIds.id("parts/essentia_terminal_has_channel");

    public static final List<Identifier> MODEL_LOCATIONS =
            List.of(MODEL_BASE, MODEL_OFF, MODEL_ON, MODEL_HAS_CHANNEL);

    private final KeyTypeSelection essentiaOnly =
            new KeyTypeSelection(this::saveChanges, keyType -> keyType == AEssentiaKeyType.INSTANCE);

    public PartEssentiaTerminal(IPartItem<?> partItem) {
        super(partItem);
    }


    @Override
    public MenuType<?> getMenuType(Player player) {
        return ModMenuTypes.ESSENTIA_TERMINAL.get();
    }

    @Override
    public boolean onUseWithoutItem(Player player, Vec3 pos) {
        if (!super.onUseWithoutItem(player, pos) && !player.level().isClientSide()) {
            MenuOpener.open(getMenuType(player), player, MenuLocators.forPart(this));
        }
        return true;
    }

    @Override
    public KeyTypeSelection getKeyTypeSelection() {
        return essentiaOnly;
    }

    @Override
    public void readFromNBT(ValueInput input) {
        super.readFromNBT(input);
        essentiaOnly.readFromNBT(input);
    }

    @Override
    public void writeToNBT(ValueOutput output) {
        super.writeToNBT(output);
        essentiaOnly.writeToNBT(output);
    }
}
