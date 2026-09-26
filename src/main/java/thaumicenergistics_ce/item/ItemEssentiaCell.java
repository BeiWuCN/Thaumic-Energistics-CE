package thaumicenergistics_ce.item;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.IBasicCellItem;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.items.contents.CellConfig;
import appeng.me.cells.BasicCellInventory;
import appeng.util.ConfigInventory;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import thaumicenergistics_ce.integration.ae2.AEssentiaKeyType;

/**
 * A storage component for essentia.
 *
 * <p>Deliberately not a storage implementation. Implementing AE2's {@link IBasicCellItem} is the whole
 * job: AE2 asks a cell item for its key type and its byte budget, and then builds its own
 * {@code BasicCellInventory} around the answer - byte accounting, per-type overhead, partitioning,
 * upgrade cards, the void card, NBT persistence and the tooltip that lists what is inside all come from
 * AE2. Writing a second one here would be a second set of those rules to keep in step, and the two would
 * drift.
 *
 * <p>That this works at all is because {@code BasicCellInventory} is key-type agnostic: it holds an
 * {@link AEKeyType}, never an item type. Essentia keys are not item keys, so the alternative would have
 * been a parallel storage stack from scratch.
 *
 * <p>Sizes follow AE2's own components: 1k, 4k, 16k and 64k bytes. At eight essentia per byte that is
 * 8192, 32768, 131072 and 524288 - the "8000" through "500k" the reference build quotes. Eight bytes per
 * type is likewise AE2's own figure, so a component holding many aspects pays for them at the same rate
 * an item component would.
 */
public class ItemEssentiaCell extends Item implements IBasicCellItem {

    /** How many upgrade cards a component takes, matching AE2's own cells. */
    private static final int UPGRADE_SLOTS = 3;

    /** Bytes each distinct aspect costs beyond the essentia itself. AE2's own figure. */
    private static final int BYTES_PER_TYPE = 8;

    /** Distinct aspects one component can hold, matching AE2's 63-type ceiling. */
    private static final int MAX_TYPES = 63;

    private final String tier;
    private final int totalBytes;
    private final double idleDrain;

    private ItemEssentiaCell(Item.Properties properties, String tier, int kilobytes, double idleDrain) {
        super(properties.stacksTo(1));
        this.tier = tier;
        this.totalBytes = kilobytes * 1024;
        this.idleDrain = idleDrain;
    }

    public static ItemEssentiaCell create1k(Item.Properties properties) {
        return new ItemEssentiaCell(properties, "1k", 1, 0.5);
    }

    public static ItemEssentiaCell create4k(Item.Properties properties) {
        return new ItemEssentiaCell(properties, "4k", 4, 1.0);
    }

    public static ItemEssentiaCell create16k(Item.Properties properties) {
        return new ItemEssentiaCell(properties, "16k", 16, 1.5);
    }

    public static ItemEssentiaCell create64k(Item.Properties properties) {
        return new ItemEssentiaCell(properties, "64k", 64, 2.0);
    }

    /**
     * The creative component.
     *
     * <p>Essentially unbounded bytes rather than infinitely many: {@code BasicCellInventory} works in
     * longs and the byte figures are ints, so a truly unbounded cell is not expressible through this
     * interface. A quarter of the int range is more essentia than a player can produce.
     */
    public static ItemEssentiaCell createCreative(Item.Properties properties) {
        return new ItemEssentiaCell(properties, "creative", Integer.MAX_VALUE / 1024, 0.0);
    }

    public String tier() {
        return tier;
    }

    // ---- IBasicCellItem ---------------------------------------------------

    @Override
    public AEKeyType getKeyType() {
        return AEssentiaKeyType.INSTANCE;
    }

    @Override
    public int getBytes(ItemStack cellItem) {
        return totalBytes;
    }

    @Override
    public int getBytesPerType(ItemStack cellItem) {
        return BYTES_PER_TYPE;
    }

    @Override
    public int getTotalTypes(ItemStack cellItem) {
        return MAX_TYPES;
    }

    @Override
    public double getIdleDrain() {
        return idleDrain;
    }

    @Override
    public boolean isStorageCell(ItemStack stack) {
        return true;
    }

    /**
     * Refuses anything that is not essentia.
     *
     * <p>Load-bearing. The byte budget and the key type check between them would already keep item keys
     * out of a cell, but a cell is also a valid target for AE2's "put anything here" paths - a cell in a
     * drive, a cell being filled by an interface - and a refusal here is the one place all of them pass
     * through.
     */
    @Override
    public boolean isBlackListed(ItemStack cellItem, AEKey requestedAddition) {
        return !(requestedAddition instanceof AEssentiaKey);
    }

    @Override
    public IUpgradeInventory getUpgrades(ItemStack stack) {
        return UpgradeInventories.forItem(stack, UPGRADE_SLOTS);
    }

    /** A partition list limited to essentia, so a cell can be told which aspects to accept. */
    @Override
    public ConfigInventory getConfigInventory(ItemStack stack) {
        return CellConfig.create(Set.of(AEssentiaKeyType.INSTANCE), stack);
    }

    /** Not fuzzy. There is no damage or durability on an aspect for a percentage to describe. */
    @Override
    public FuzzyMode getFuzzyMode(ItemStack stack) {
        return FuzzyMode.IGNORE_ALL;
    }

    @Override
    public void setFuzzyMode(ItemStack stack, FuzzyMode mode) {
        // Nothing to store: the mode is fixed.
    }

    @Override
    public void appendHoverText(
            ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        addCellInformationToTooltip(stack, tooltip);
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        return getCellTooltipImage(stack);
    }

    // ---- Partitioning, shared with the cell workbench ----------------------

    /** The cell's partition list, or {@code null} when the stack is not one of ours. */
    public static @Nullable ConfigInventory partitionOf(ItemStack stack) {
        return stack.getItem() instanceof ItemEssentiaCell cell ? cell.getConfigInventory(stack) : null;
    }

    /** The aspects a cell is partitioned to, in slot order. */
    public static List<ResourceLocation> partitionedAspects(ItemStack stack) {
        ConfigInventory inventory = partitionOf(stack);
        if (inventory == null) {
            return List.of();
        }
        List<ResourceLocation> aspects = new ArrayList<>();
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (inventory.getKey(slot) instanceof AEssentiaKey key) {
                aspects.add(key.getId());
            }
        }
        return aspects;
    }

    /** Adds an aspect to the partition. @return false when it is already there or the list is full */
    public static boolean addPartition(ItemStack stack, ResourceLocation aspectId) {
        ConfigInventory inventory = partitionOf(stack);
        if (inventory == null) {
            return false;
        }
        AEssentiaKey key = AEssentiaKey.of(aspectId);
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (key.equals(inventory.getKey(slot))) {
                return false;
            }
        }
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (inventory.getKey(slot) == null) {
                inventory.insert(slot, key, 1, Actionable.MODULATE);
                return true;
            }
        }
        return false;
    }

    /** Removes an aspect from the partition. @return false when it was not there */
    public static boolean removePartition(ItemStack stack, ResourceLocation aspectId) {
        ConfigInventory inventory = partitionOf(stack);
        if (inventory == null) {
            return false;
        }
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (inventory.getKey(slot) instanceof AEssentiaKey key && key.getId().equals(aspectId)) {
                inventory.setStack(slot, null);
                return true;
            }
        }
        return false;
    }

    /** Empties the partition list. */
    public static void clearPartition(ItemStack stack) {
        ConfigInventory inventory = partitionOf(stack);
        if (inventory != null) {
            inventory.clear();
        }
    }

    /**
     * Partitions a cell to whatever it already holds.
     *
     * <p>The workbench's "partition to contents", and the reason it is here rather than on the workbench:
     * it reads the cell's own inventory, which means building AE2's inventory for it - and that is a
     * question about this item, not about the block the player happens to be standing at.
     */
    public static void partitionToContents(ItemStack stack) {
        ConfigInventory partition = partitionOf(stack);
        if (partition == null) {
            return;
        }
        BasicCellInventory cell = BasicCellInventory.createInventory(stack, null);
        if (cell == null) {
            return;
        }
        partition.clear();
        KeyCounter contents = new KeyCounter();
        cell.getAvailableStacks(contents);
        int slot = 0;
        for (var entry : contents) {
            if (slot >= partition.size()) {
                return;
            }
            if (entry.getKey() instanceof AEssentiaKey key) {
                partition.insert(slot++, key, 1, Actionable.MODULATE);
            }
        }
    }

    /** The cell's contents, as aspect stacks, for the workbench and the terminal. */
    public static List<GenericStack> contentsOf(ItemStack stack) {
        BasicCellInventory cell = BasicCellInventory.createInventory(stack, null);
        if (cell == null) {
            return List.of();
        }
        KeyCounter counter = new KeyCounter();
        cell.getAvailableStacks(counter);
        List<GenericStack> contents = new ArrayList<>();
        for (var entry : counter) {
            contents.add(new GenericStack(entry.getKey(), entry.getLongValue()));
        }
        return contents;
    }
}
