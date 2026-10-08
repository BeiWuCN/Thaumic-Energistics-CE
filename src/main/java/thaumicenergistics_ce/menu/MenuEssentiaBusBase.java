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
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.network.EssentiaBusConfigPayload;
import thaumicenergistics_ce.network.EssentiaBusReceiver;
import thaumicenergistics_ce.util.ThELog;

/**
 * 源质总线配置界面的共同部分：配置网格，以及其中有多少可用。
 * <ul>
 *   <li>共用是为了槽位索引必须算对：JEI 问本类某个配置槽位在哪，
 *       再抄一份算法就多一次算错的机会。
 * </ul>
 */
public abstract class MenuEssentiaBusBase<T extends IUpgradeableObject> extends UpgradeableMenu<T>
        implements EssentiaBusReceiver {

    protected MenuEssentiaBusBase(MenuType<?> menuType, int id, Inventory playerInventory, T host) {
        super(menuType, id, playerInventory, host);
    }

    /**
     * 配置网格：固定两行九列，永远可用。把槽位做成可扩展的，像
     * {@code IOBusMenu} 那样，会得到一个谁都写不进去的网格：客户端和服务端不一致。
     */
    @Override
    protected void setupConfig() {
        ConfigMenuInventory inv = configInventory().createMenuWrapper();
        for (int i = 0; i < CONFIG_SLOTS; i++) {
            addSlot(new FakeSlot(inv, i), SlotSemantics.CONFIG);
        }
    }

    /** 两行九列。 */
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
            Identifier aspectId,
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
            // 服务端不认识的 id：丢掉它胜过留下一个永远匹配不上的过滤条目。
            ThELog.LOG.warn("[bus-config] the server cannot resolve aspect {}", aspectId);
            return;
        }

        var key = thaumicenergistics_ce.integration.ae2.AEssentiaKey.of(aspect);
        if (key == null) {
            // 不是注册表条目：没有 id，该过滤条目永远匹配不上。
            ThELog.LOG.warn("[bus-config] aspect {} is not a registry entry", aspectId);
            return;
        }
        setConfigSlot(configSlot, new GenericStack(key, 1));
        // 立刻回读：把 "wrote" 与 "now holds" 当成两个独立事实，方便追故障。
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
