package thaumicenergistics_ce.part;

import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.util.KeyTypeSelection;
import appeng.items.parts.PartModels;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.parts.PartModel;
import appeng.parts.reporting.AbstractTerminalPart;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.phys.Vec3;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.init.ModMenuTypes;
import thaumicenergistics_ce.integration.ae2.AEssentiaKeyType;

/**
 * The Essentia Terminal, as a part that goes on a cable.
 *
 * <p>An AE2 terminal with one thing changed: it lists essentia and nothing else. That is expressed through
 * {@link KeyTypeSelection}, which is the interface AE2 already uses to decide which key types a terminal
 * offers - so the filtering is AE2's own machinery rather than a special case bolted onto its item list.
 *
 * <p>The parent part keeps a selection that allows every type. Ours replaces it, and the selection also
 * has to be read and written by hand for that to stick: the parent's fields are private, so its own
 * read/write would otherwise save the permissive selection over ours on every load and save. The
 * reference build carries the same pair of overrides for the same reason.
 */
public class PartEssentiaTerminal extends AbstractTerminalPart {

    @PartModels
    public static final ResourceLocation MODEL_BASE = ThEIds.id("parts/essentia_terminal_base");

    @PartModels
    public static final ResourceLocation MODEL_OFF = ThEIds.id("parts/essentia_terminal_off");

    @PartModels
    public static final ResourceLocation MODEL_ON = ThEIds.id("parts/essentia_terminal_on");

    @PartModels
    public static final ResourceLocation MODEL_HAS_CHANNEL = ThEIds.id("parts/essentia_terminal_has_channel");

    private static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE, MODEL_OFF, MODEL_STATUS_OFF);
    private static final IPartModel MODELS_ON = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_ON);
    private static final IPartModel MODELS_HAS_CHANNEL =
            new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_HAS_CHANNEL);

    /** Essentia only. Everything else the terminal would list - items, fluids - is filtered out. */
    private final KeyTypeSelection essentiaOnly =
            new KeyTypeSelection(this::saveChanges, keyType -> keyType == AEssentiaKeyType.INSTANCE);

    public PartEssentiaTerminal(IPartItem<?> partItem) {
        super(partItem);
    }

    @Override
    public IPartModel getStaticModels() {
        return selectModel(MODELS_OFF, MODELS_ON, MODELS_HAS_CHANNEL);
    }

    @Override
    public MenuType<?> getMenuType(Player player) {
        return ModMenuTypes.ESSENTIA_TERMINAL.get();
    }

    @Override
    public boolean onUseWithoutItem(Player player, Vec3 pos) {
        if (!super.onUseWithoutItem(player, pos) && !player.level().isClientSide) {
            MenuOpener.open(getMenuType(player), player, MenuLocators.forPart(this));
        }
        return true;
    }

    @Override
    public KeyTypeSelection getKeyTypeSelection() {
        return essentiaOnly;
    }

    @Override
    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.readFromNBT(data, registries);
        // After the parent, so ours is the selection NBT is read into.
        essentiaOnly.readFromNBT(data, registries);
    }

    @Override
    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.writeToNBT(data, registries);
        // After the parent, so ours overwrites what the parent wrote for its own selection.
        essentiaOnly.writeToNBT(data);
    }
}
