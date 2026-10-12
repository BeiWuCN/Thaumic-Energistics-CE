package thaumicenergistics_ce.integration.ae2.bus;

import appeng.api.parts.IPartHost;
import appeng.core.definitions.AEParts;
import appeng.parts.automation.ExportBusPart;
import appeng.parts.automation.ImportBusPart;
import appeng.parts.automation.UpgradeablePart;
import appeng.parts.storagebus.StorageBusPart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ItemLike;
import thaumicenergistics_ce.config.ThEConfig;
import thaumicenergistics_ce.registry.TECEItems;

public final class EssentiaBusAccess {
    public static final int SLOT_COUNT = 6;
    public static final int AMOUNT_PER_OPERATION = 8;

    private EssentiaBusAccess() {}

    public static boolean isBus(ItemLike item) {
        var actual = item.asItem();
        return actual == AEParts.IMPORT_BUS.asItem() || actual == AEParts.EXPORT_BUS.asItem()
                || actual == AEParts.STORAGE_BUS.asItem();
    }

    public static boolean hasCard(Object host) {
        return (host instanceof ImportBusPart || host instanceof ExportBusPart || host instanceof StorageBusPart)
                && ((UpgradeablePart) host).getUpgrades().isInstalled(TECEItems.ESSENTIA_ACCESS_CARD.get());
    }

    public static boolean enabled(Object host) {
        return ThEConfig.BUS_ACCESS.get() && hasCard(host);
    }

    public static UpgradeablePart facingBus(ServerLevel level, BlockPos target, Direction face) {
        var hostPos = target.relative(face);
        if (!level.isLoaded(hostPos) || !(level.getBlockEntity(hostPos) instanceof IPartHost host)) {
            return null;
        }
        var part = host.getPart(face.getOpposite());
        return part instanceof UpgradeablePart bus && isBus(bus.getPartItem()) ? bus : null;
    }
}
