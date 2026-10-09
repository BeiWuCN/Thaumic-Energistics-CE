package thaumicenergistics_ce.interfaceaccess;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.util.ConfigInventory;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import thaumicenergistics_ce.util.ThELog;

/**
 * 带本访问卡的接口的两个配置行，整体读写：配置行说哪些要素能进来，
 * 存储行是取卡时网格收回的东西。不绑轮次，手里没有宿主也能推敲一行。
 */
public final class EssentiaInterfaceRows {

    private EssentiaInterfaceRows() {}

    /** 配置行归约成源质键；空结果代表「完全没有过滤」。 */
    static List<AEKey> whitelist(ConfigInventory row) {
        List<AEKey> listed = new ArrayList<>();
        for (int slot = 0; slot < row.size(); slot++) {
            AEKey key = row.getKey(slot);
            if (key instanceof AEssentiaKey) {
                listed.add(key);
            }
        }
        return listed;
    }

    /**
     * 配置行放不放某个键进来。空行就是没过滤：不是源质的条目从不计数，
     * 故只放物品或流体的行跟空行一样。
     */
    public static boolean mayEnter(List<AEKey> allowed, AEKey key) {
        boolean filtered = false;
        for (AEKey entry : allowed) {
            if (!(entry instanceof AEssentiaKey)) {
                continue;
            }
            filtered = true;
            if (entry.equals(key)) {
                return true;
            }
        }
        return !filtered;
    }

    /**
     * 清空刚取出卡的接口的两行：标记消失，存储行交还网格。
     * 网格拒收的要素丢掉；别的类型的键留下。
     */
    public static void releaseRows(
            ConfigInventory config,
            ConfigInventory storage,
            @Nullable MEStorage network,
            IActionSource source) {
        config.clear();
        for (int slot = 0; slot < storage.size(); slot++) {
            GenericStack held = storage.getStack(slot);
            if (held == null) {
                continue;
            }
            long rest = held.amount() - returnToNetwork(held, network, source);
            if (rest <= 0) {
                storage.setStack(slot, null);
            } else if (held.what() instanceof AEssentiaKey) {
                ThELog.LOG.info(
                        "[essentia-interface] discarding {} of {} as the card comes out", rest, held.what());
                storage.setStack(slot, null);
            } else {
                storage.setStack(slot, new GenericStack(held.what(), rest));
            }
        }
    }

    /**
     * 把即将丢掉的存储行里的要素清出来，砸接口绝不会把要素掉在地上。
     * 要素没有可掉落的物品形态；其余的交给网格。
     */
    public static void rescueEssentia(
            ConfigInventory storage,
            @Nullable MEStorage network,
            IActionSource source) {
        for (int slot = 0; slot < storage.size(); slot++) {
            GenericStack held = storage.getStack(slot);
            if (held == null || !(held.what() instanceof AEssentiaKey)) {
                continue;
            }
            long rest = held.amount() - returnToNetwork(held, network, source);
            if (rest > 0) {
                ThELog.LOG.info(
                        "[essentia-interface] discarding {} of {} as the interface goes", rest, held.what());
            }
            storage.setStack(slot, null);
        }
    }

    /** 某一行有没有要素，这是分辨我们的接口与普通接口的依据。 */
    public static boolean holdsEssentia(ConfigInventory row) {
        for (int slot = 0; slot < row.size(); slot++) {
            if (row.getKey(slot) instanceof AEssentiaKey) {
                return true;
            }
        }
        return false;
    }

    /** 网格会收下手头物品堆的多少；网格没了或满了就一点不收。 */
    private static long returnToNetwork(
            GenericStack held,
            @Nullable MEStorage network,
            IActionSource source) {
        if (network == null) {
            return 0;
        }
        return network.insert(held.what(), held.amount(), Actionable.MODULATE, source);
    }
}
