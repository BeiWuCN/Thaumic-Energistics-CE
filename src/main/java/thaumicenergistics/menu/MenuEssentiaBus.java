package thaumicenergistics.menu;

import appeng.core.definitions.AEItems;
import thaumicenergistics.ThaumicEnergistics;
import appeng.menu.SlotSemantics;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.implementations.UpgradeableMenu;
import appeng.api.upgrades.IUpgradeableObject;
import java.util.List;
import org.jspecify.annotations.Nullable;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;

/**
 * What the essentia buses' config screens have in common: the config grid, and how much of it is usable.
 *
 * <p>Shared because the slot index is the part that has to be right - JEI asks this class where a config
 * slot is, and a second copy of that arithmetic is a second chance to get it wrong. The two buses differ in
 * what they do with the config, not in how it is presented or addressed.
 */
public abstract class MenuEssentiaBus<T extends IUpgradeableObject> extends UpgradeableMenu<T> {

    protected MenuEssentiaBus(MenuType<?> menuType, int id, Inventory playerInventory, T host) {
        super(menuType, id, playerInventory, host);
    }

    /**
     * The config grid: two fixed rows of nine, always usable. This used to call
     * {@code addExpandableConfigSlots} and override {@code isSlotEnabled} as {@code IOBusMenu} does, which
     * gave a grid nothing could write to - the client offered cells from its own copy of the upgrade inventory
     * and the server refused them from its own. Eighteen plain slots and an unconditional {@code true}, as the
     * reference does, leave the two sides nothing to disagree about.
     */
    @Override
    protected void setupConfig() {
        appeng.util.ConfigMenuInventory inv = configInventory().createMenuWrapper();
        for (int i = 0; i < CONFIG_SLOTS; i++) {
            addSlot(new appeng.menu.slot.FakeSlot(inv, i), SlotSemantics.CONFIG);
        }
    }

    /** Two rows of nine. */
    public static final int CONFIG_SLOTS = 18;

    /** Every config cell is usable, as in the reference. */
    @Override
    public boolean isSlotEnabled(int index) {
        return index >= 0 && index < CONFIG_SLOTS;
    }

    /** How many config slots the grid has. The reference's eighteen, two rows of nine. */
    public int getConfigSlotCount() {
        return CONFIG_SLOTS;
    }

    /**
     * Sets one config slot to an aspect, or clears it. Server side; called from
     * {@link thaumicenergistics.network.EssentiaBusConfigPayload}.
     *
     * <p>Writing the config inventory directly is the point - AE2's ghost-slot route unwraps the key through
     * {@code AEItemKey} and an essentia key is not an item, which is why a JEI filter appeared and then
     * vanished. The aspect is resolved against the sender's level, hence the player parameter.
     */
    public void setConfigAspect(
            int configSlot,
            net.minecraft.resources.ResourceLocation aspectId,
            net.minecraft.world.entity.player.Player player) {
        if (configSlot < 0 || configSlot >= getConfigSlotCount()) {
            ThaumicEnergistics.LOG.warn(
                    "[bus-config] slot {} is out of range (grid holds {})", configSlot, getConfigSlotCount());
            return;
        }
        if (!isSlotEnabled(configSlot)) {
            ThaumicEnergistics.LOG.warn(
                    "[bus-config] slot {} is locked: {} capacity card(s) installed",
                    configSlot, getUpgrades().getInstalledUpgrades(appeng.core.definitions.AEItems.CAPACITY_CARD));
            return;
        }
        if (thaumicenergistics.network.EssentiaBusConfigPayload.CLEAR.equals(aspectId)) {
            setConfigSlot(configSlot, null);
            return;
        }

        net.minecraft.core.Holder<com.leclowndu93150.thaumaturge.api.aspect.IAspect> aspect =
                com.leclowndu93150.thaumaturge.api.aspect.Aspects.resolve(
                        player.level(),
                        net.minecraft.resources.ResourceKey.create(
                                com.leclowndu93150.thaumaturge.api.aspect.IAspect.REGISTRY_KEY, aspectId));
        if (aspect == null) {
            // An id the server does not know: dropping it beats a filter entry that can never match anything.
            ThaumicEnergistics.LOG.warn("[bus-config] the server cannot resolve aspect {}", aspectId);
            return;
        }

        var key = thaumicenergistics.integration.ae2.AEssentiaKey.of(aspect);
        if (key == null) {
            // Not registry-backed: no id, so the filter entry could never match anything.
            ThaumicEnergistics.LOG.warn("[bus-config] aspect {} is not a registry entry", aspectId);
            return;
        }
        setConfigSlot(configSlot, new appeng.api.stacks.GenericStack(key, 1));
        // Read straight back: "wrote" and "now holds" as two separate facts, for the failure being chased.
        ThaumicEnergistics.LOG.info(
                "[bus-config] wrote {} to slot {}; it now holds {}",
                key, configSlot, configFor(configSlot));
    }

    /**
     * Writes one config position. {@code ConfigInventory} is the single source of truth - the menu slots are
     * views onto it - so writing here is enough, and the client's slots follow when the menu syncs. See
     * {@link thaumicenergistics.network.EssentiaBusConfigPayload} for why the item route does not work for
     * essentia.
     */
    public void setConfigSlot(int configSlot, appeng.api.stacks.GenericStack stack) {
        configInventory().setStack(configSlot, stack);
    }

    /** The menu slot that currently stands for a config position, or null if there is none. */
    public @Nullable Slot menuSlotFor(int configSlot) {
        List<Slot> configSlots = getSlots(SlotSemantics.CONFIG);
        if (configSlot < 0 || configSlot >= configSlots.size()) {
            return null;
        }
        return configSlots.get(configSlot);
    }

    /**
     * Looked up among the slots registered for {@link SlotSemantics#CONFIG} rather than counted from a fixed
     * offset. {@code UpgradeableMenu} calls {@code setupConfig()} before {@code createPlayerInventorySlots()},
     * so config slot 0 is slot 0; the old offset of 36 pointed every drop target into the player's inventory,
     * four rows away.
     */
    public int configSlotIndex(int configSlot) {
        List<Slot> configSlots = getSlots(SlotSemantics.CONFIG);
        return configSlots.get(configSlot).index;
    }

    /** What a config slot currently holds, for diagnostics. */
    public String configFor(int configSlot) {
        if (configSlot < 0 || configSlot >= getConfigSlotCount()) {
            return "out-of-range";
        }
        var stack = configInventory().getStack(configSlot);
        return stack == null ? "empty" : stack.what() + " x" + stack.amount();
    }

    /** The host's config inventory, which each bus declares for itself. */
    protected abstract appeng.util.ConfigInventory configInventory();
}
