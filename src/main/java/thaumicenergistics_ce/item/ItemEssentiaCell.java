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
 * A storage component for essentia: byte accounting, partitioning, upgrades, NBT and the tooltip stay AE2's.
 * <ul>
 * <li>AE2 builds its own {@code BasicCellInventory} from the key type and byte budget this item reports.
 * <li>Sizes follow AE2's 1k/4k/16k/64k: eight essentia per byte, so 8192/32768/131072/524288.
 * <li>Eight bytes per type and the 63-type ceiling are likewise AE2's own figures.
 * </ul>
 */
public class ItemEssentiaCell extends Item implements IBasicCellItem {

    public static final int UPGRADE_SLOTS = 3;

    private static final int BYTES_PER_TYPE = 8;

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
     * The creative component: unbounded rather than infinite, since {@code BasicCellInventory} works
     * in longs and the byte figures are ints.
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
     * Refuses anything that is not essentia. A cell in a drive or filled by an interface reaches it
     * through the same paths, so this refusal is the one place all of them pass through.
     */
    @Override
    public boolean isBlackListed(ItemStack cellItem, AEKey requestedAddition) {
        return !(requestedAddition instanceof AEssentiaKey);
    }

    @Override
    public IUpgradeInventory getUpgrades(ItemStack stack) {
        return UpgradeInventories.forItem(stack, UPGRADE_SLOTS);
    }

    @Override
    public ConfigInventory getConfigInventory(ItemStack stack) {
        return CellConfig.create(Set.of(AEssentiaKeyType.INSTANCE), stack);
    }

    @Override
    public FuzzyMode getFuzzyMode(ItemStack stack) {
        return FuzzyMode.IGNORE_ALL;
    }

    @Override
    public void setFuzzyMode(ItemStack stack, FuzzyMode mode) {
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


    public static @Nullable ConfigInventory partitionOf(ItemStack stack) {
        return stack.getItem() instanceof ItemEssentiaCell cell ? cell.getConfigInventory(stack) : null;
    }

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

    public static void clearPartition(ItemStack stack) {
        ConfigInventory inventory = partitionOf(stack);
        if (inventory != null) {
            inventory.clear();
        }
    }

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
