package thaumicenergistics_ce.menu;

import appeng.api.stacks.GenericStack;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.core.definitions.AEItems;
import appeng.menu.SlotSemantics;
import appeng.menu.implementations.UpgradeableMenu;
import appeng.menu.slot.FakeSlot;
import appeng.util.ConfigInventory;
import appeng.util.ConfigMenuInventory;
import com.leclowndu93150.thaumaturge.api.aspect.Aspects;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.network.EssentiaBusConfigPayload;
import thaumicenergistics_ce.network.EssentiaBusReceiver;
import thaumicenergistics_ce.util.ThELog;

/**
 * What the essentia buses' config screens have in common: the config grid, and how much of it is
 * usable. It is shared for the slot index, which has to be right, because JEI asks this class
 * where a config slot is and a second copy of the arithmetic would be a second chance to get it
 * wrong.
 */
public abstract class MenuEssentiaBusBase<T extends IUpgradeableObject> extends UpgradeableMenu<T>
        implements EssentiaBusReceiver {

    protected MenuEssentiaBusBase(MenuType<?> menuType, int id, Inventory playerInventory, T host) {
        super(menuType, id, playerInventory, host);
    }

    /**
     * The config grid: two fixed rows of nine, always usable. Making the slots expandable, as
     * {@code IOBusMenu} does, gives a grid nothing can write to: client and server disagree.
     */
    @Override
    protected void setupConfig() {
        ConfigMenuInventory inv = configInventory().createMenuWrapper();
        for (int i = 0; i < CONFIG_SLOTS; i++) {
            addSlot(new FakeSlot(inv, i), SlotSemantics.CONFIG);
        }
    }

    /** Two rows of nine. */
    public static final int CONFIG_SLOTS = 18;

    @Override
    public boolean isSlotEnabled(int index) {
        return index >= 0 && index < CONFIG_SLOTS;
    }

    public int getConfigSlotCount() {
        return CONFIG_SLOTS;
    }

    @Override
    public void setConfigAspect(
            int configSlot,
            ResourceLocation aspectId,
            Player player) {
        if (configSlot < 0 || configSlot >= getConfigSlotCount()) {
            ThELog.LOG.warn(
                    "[bus-config] slot {} is out of range (grid holds {})", configSlot, getConfigSlotCount());
            return;
        }
        if (!isSlotEnabled(configSlot)) {
            ThELog.LOG.warn(
                    "[bus-config] slot {} is locked: {} capacity card(s) installed",
                    configSlot, getUpgrades().getInstalledUpgrades(AEItems.CAPACITY_CARD));
            return;
        }
        if (EssentiaBusConfigPayload.CLEAR.equals(aspectId)) {
            setConfigSlot(configSlot, null);
            return;
        }

        Holder<IAspect> aspect =
                Aspects.resolve(
                        player.level(),
                        ResourceKey.create(
                                IAspect.REGISTRY_KEY, aspectId));
        if (aspect == null) {
            // An id the server does not know: dropping it beats a filter entry that can never match anything.
            ThELog.LOG.warn("[bus-config] the server cannot resolve aspect {}", aspectId);
            return;
        }

        var key = thaumicenergistics_ce.integration.ae2.AEssentiaKey.of(aspect);
        if (key == null) {
            // Not registry-backed: no id, so the filter entry could never match anything.
            ThELog.LOG.warn("[bus-config] aspect {} is not a registry entry", aspectId);
            return;
        }
        setConfigSlot(configSlot, new GenericStack(key, 1));
        // Read straight back: "wrote" and "now holds" as two separate facts, for the failure being chased.
        ThELog.LOG.info(
                "[bus-config] wrote {} to slot {}; it now holds {}",
                key, configSlot, configFor(configSlot));
    }

    public void setConfigSlot(int configSlot, GenericStack stack) {
        configInventory().setStack(configSlot, stack);
    }

    public @Nullable Slot menuSlotFor(int configSlot) {
        List<Slot> configSlots = getSlots(SlotSemantics.CONFIG);
        if (configSlot < 0 || configSlot >= configSlots.size()) {
            return null;
        }
        return configSlots.get(configSlot);
    }

    public int configSlotIndex(int configSlot) {
        List<Slot> configSlots = getSlots(SlotSemantics.CONFIG);
        return configSlots.get(configSlot).index;
    }

    @Override
    public String configFor(int configSlot) {
        if (configSlot < 0 || configSlot >= getConfigSlotCount()) {
            return "out-of-range";
        }
        var stack = configInventory().getStack(configSlot);
        return stack == null ? "empty" : stack.what() + " x" + stack.amount();
    }

    @Override
    public int containerId() {
        return containerId;
    }

    protected abstract ConfigInventory configInventory();
}
