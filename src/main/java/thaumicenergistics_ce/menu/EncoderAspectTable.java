package thaumicenergistics_ce.menu;

import com.leclowndu93150.thaumaturge.api.aspect.AspectIndexAccess;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.capability.KnowledgeAccess;
import com.leclowndu93150.thaumaturge.api.research.pool.AspectPoolAccess;
import com.leclowndu93150.thaumaturge.api.research.scan.ScanKeys;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.blockentity.BlockEntityDistillationEncoder;
import thaumicenergistics_ce.util.ThELog;

/** 编码器的要素行：源物品提供什么、各有多少、哪些凹槽可见、选中了哪个。
 * 两侧都从同步的物品推导，不会不一致。 */
final class EncoderAspectTable {

    private final MenuDistillationEncoder menu;

    private final @Nullable BlockEntityDistillationEncoder encoder;

    /** 由 {@link #refresh} 写入的视图，玩家写不到它。 */
    private final SimpleContainer aspectDisplay;

    private final SimpleContainer selectedDisplay;

    private List<Holder<IAspect>> aspects = List.of();

    private List<Integer> aspectAmounts = List.of();

    private boolean[] revealedWells = new boolean[MenuDistillationEncoder.ASPECT_SLOTS];

    private int revealedTotal;

    private int localSelection = -1;

    private ItemStack lastSourceItem = ItemStack.EMPTY;

    private static final boolean TRACE = System.getenv("THAUMICENERGISTICS_ENCODER_TRACE") != null;

    private ItemStack tracedSource = ItemStack.EMPTY;

    private int tracedRevealed = -1;

    private int tracedPick = -1;

    EncoderAspectTable(
            MenuDistillationEncoder menu,
            @Nullable BlockEntityDistillationEncoder encoder,
            SimpleContainer aspectDisplay,
            SimpleContainer selectedDisplay) {
        this.menu = menu;
        this.encoder = encoder;
        this.aspectDisplay = aspectDisplay;
        this.selectedDisplay = selectedDisplay;
    }

    /** 源物品变化时刷新这一行。
     * 客户端菜单收不到通知（{@link MenuDistillationEncoder#broadcastChanges}），界面绘制前自己调它。 */
    void ensure() {
        if (!ItemStack.matches(menu.slots.get(MenuDistillationEncoder.MENU_SOURCE).getItem(), lastSourceItem)) {
            refresh();
        }
    }

    void refresh() {
        List<Holder<IAspect>> found;
        List<Integer> foundAmounts = new ArrayList<>();
        if (encoder != null) {
            found = encoder.availableAspects();
            for (Holder<IAspect> aspect : found) {
                foundAmounts.add((int) Math.min(Integer.MAX_VALUE, encoder.yieldFor(aspect)));
            }
        } else if (!menu.slots.isEmpty()) {
            found = deriveAspects(
                    menu.slots.get(MenuDistillationEncoder.PLAYER_SLOTS + MenuDistillationEncoder.IDX_SOURCE)
                            .getItem(),
                    foundAmounts);
        } else {
            found = List.of();
        }

        if (!found.equals(aspects)) {
            aspects = found;
            if (localSelection >= aspects.size()) {
                localSelection = -1;
            }
        }

        aspectAmounts = List.copyOf(foundAmounts);

        // 每个凹槽一个标志。两个判断都要：物品已扫描、要素已发现；Thaumaturge 把元初要素都算已知。
        ItemStack source = menu.slots.get(MenuDistillationEncoder.MENU_SOURCE).getItem();
        lastSourceItem = source.copy();
        boolean itemScanned = sourceIsScanned(source);
        boolean[] revealedWells = new boolean[MenuDistillationEncoder.ASPECT_SLOTS];
        int revealedTotal = 0;
        for (int i = 0; i < MenuDistillationEncoder.ASPECT_SLOTS && i < aspects.size(); i++) {
            if (itemScanned && AspectPoolAccess.isDiscovered(menu.owner, aspects.get(i))) {
                revealedWells[i] = true;
                revealedTotal++;
            }
        }
        traceSource(source, itemScanned, revealedTotal);
        this.revealedWells = revealedWells;
        this.revealedTotal = revealedTotal;

        // 凹槽刻意留空，界面自己画图标、数量和选中框；放物品堆会多渲染一个物品。
        for (int i = 0; i < MenuDistillationEncoder.ASPECT_SLOTS; i++) {
            aspectDisplay.setItem(i, ItemStack.EMPTY);
        }

        tracePick();
        // 选中的凹槽也清空，由界面像别的凹槽一样画出来。
        selectedDisplay.setItem(0, ItemStack.EMPTY);
    }

    private boolean sourceIsScanned(ItemStack source) {
        return !source.isEmpty()
                && KnowledgeAccess.of(menu.owner).isResearchKnown(ScanKeys.item(source.getItem()));
    }

    /** 开关打开时每次源物品变化记一次揭示判断；『要素不显示』有三种相似成因。 */
    private void traceSource(ItemStack source, boolean itemScanned, int revealedTotal) {
        if (!TRACE || (ItemStack.matches(source, tracedSource) && revealedTotal == tracedRevealed)) {
            return;
        }
        tracedSource = source.copy();
        tracedRevealed = revealedTotal;
        ThELog.LOG.info(
                "[encoder] source={} scanned={} itemAspects={} revealed={}",
                source.isEmpty() ? "empty" : source.getItem(),
                itemScanned,
                aspects.size(),
                revealedTotal);
    }

    /** 记录选中变更与拒绝原因；两种情况凹槽都不画东西。 */
    private void tracePick() {
        int raw = pickedIndexRaw();
        if (!TRACE || raw == tracedPick) {
            return;
        }
        tracedPick = raw;
        String why = raw < 0
                ? "nothing picked"
                : raw >= aspects.size()
                        ? "refused: no aspect at " + raw
                        : !isRevealed(raw) ? "refused: aspect " + raw + " is not revealed" : "accepted";
        ThELog.LOG.info(
                "[encoder] pick {} of {} -> {} ({}), {} side",
                raw,
                aspects.size(),
                pickedIndex(),
                why,
                encoder != null ? "server" : "client");
    }

    /** 源槽有东西却提供不了任何要素时为 true；已扫描的物品一律 false。 */
    boolean revealsNothing() {
        return !menu.slots.get(MenuDistillationEncoder.MENU_SOURCE).getItem().isEmpty() && revealedTotal == 0;
    }

    int amountFor(int index) {
        return index >= 0 && index < aspectAmounts.size() ? aspectAmounts.get(index) : 0;
    }

    /** 玩家是否已发现该要素；没发现的凹槽画空，点击也拒绝。 */
    boolean isRevealed(int index) {
        return index >= 0 && index < revealedWells.length && revealedWells[index];
    }

    int revealedCount() {
        return revealedTotal;
    }

    /** 被选中的要素；槽位里没有东西，只能从这里读。
     * 没选中或选中项不可见时为 null。 */
    @Nullable Holder<IAspect> pickedAspect() {
        int picked = pickedIndex();
        return picked >= 0 ? aspects.get(picked) : null;
    }

    int pickedAmount() {
        return amountFor(pickedIndex());
    }

    /** 选中的要素索引，没有为 {@code -1}。
     * 每次现算不缓存；选中项会在服务端没有消息的情况下变。 */
    int pickedIndex() {
        int picked = pickedIndexRaw();
        return picked >= 0 && picked < aspects.size() && isRevealed(picked) ? picked : -1;
    }

    private int pickedIndexRaw() {
        return encoder != null ? encoder.selectedAspectIndex() : localSelection;
    }

    void select(int index) {
        localSelection = index;
    }

    int localSelection() {
        return localSelection;
    }

    List<Holder<IAspect>> aspects() {
        return aspects;
    }

    int aspectCount() {
        return aspects.size();
    }

    /** 一次遍历同时得出要素与数量，数量写进 {@code amountsOut}。
     * 界面开着时它每 tick 都跑。 */
    private static List<Holder<IAspect>> deriveAspects(ItemStack source, List<Integer> amountsOut) {
        if (source.isEmpty()) {
            return List.of();
        }
        var composition = AspectIndexAccess.of(source);
        if (composition == null || composition.isEmpty()) {
            return List.of();
        }
        List<Holder<IAspect>> found = new ArrayList<>();
        List<Integer> amounts = new ArrayList<>();
        for (var entry : composition.entries()) {
            if (entry.amount() > 0 && found.size() < MenuDistillationEncoder.ASPECT_SLOTS) {
                found.add(entry.aspect());
                amounts.add((int) Math.min(Integer.MAX_VALUE, entry.amount()));
            }
        }

        // 两侧给要素编号的顺序要一致，选中项按索引传输。
        // 排索引、不排要素，数量才跟得住自己的要素。
        List<Integer> order = new ArrayList<>(found.size());
        for (int i = 0; i < found.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator.comparing(i -> aspectId(found.get(i))));
        for (int i : order) {
            amountsOut.add(amounts.get(i));
        }
        List<Holder<IAspect>> sorted = new ArrayList<>(found.size());
        for (int i : order) {
            sorted.add(found.get(i));
        }
        return List.copyOf(sorted);
    }

    private static String aspectId(Holder<IAspect> aspect) {
        return aspect.unwrapKey().map(k -> k.identifier().toString()).orElse("");
    }
}
