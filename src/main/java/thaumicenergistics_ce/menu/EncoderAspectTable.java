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

/** 编码器的要素行：源物品提供什么、各有多少、玩家可以看到哪些凹槽，
 * 以及哪一个被选中。两侧都由同步的物品推导它，所以两者不可能不一致。 */
final class EncoderAspectTable {

    private final MenuDistillationEncoder menu;

    private final @Nullable BlockEntityDistillationEncoder encoder;

    /** 由 {@link #refresh} 写入的视图，永远不由玩家写入。 */
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

    /** 源物品变化时把这一行更新到最新。没有任何机制通知客户端菜单去
     * 推导它（{@link MenuDistillationEncoder#broadcastChanges} 由
     * 服务端的 tick 驱动），所以界面在绘制之前调用这个方法。 */
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

        // 每个凹槽一个标志，而不是一个更短的列表：选中项以行内索引的形式传输。两个判断
        // 都需要——物品已扫描与要素已发现——因为 Thaumaturge 把每个元初要素都算作已知。
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

        // 刻意留空，参考实现也是这么留的：界面会绘制图标、它的数量
        // 以及选中框；这里放物品堆会在要素图标下再渲染一个物品。
        for (int i = 0; i < MenuDistillationEncoder.ASPECT_SLOTS; i++) {
            aspectDisplay.setItem(i, ItemStack.EMPTY);
        }

        tracePick();
        // 被选中的凹槽也会被清空，并像其它凹槽一样由界面绘制。
        selectedDisplay.setItem(0, ItemStack.EMPTY);
    }

    private boolean sourceIsScanned(ItemStack source) {
        return !source.isEmpty()
                && KnowledgeAccess.of(menu.owner).isResearchKnown(ScanKeys.item(source.getItem()));
    }

    /** 在开关打开时，每个源物品记录一次揭示判断的结果：'要素
     * 不显示' 有三种看起来相似的成因。 */
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

    /** 记录选中项的变更以及某次为何被拒绝：无论哪种情况凹槽都不绘制任何东西。 */
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

    /** 当源凹槽持有东西、但无法由它提供任何要素时返回 true；已扫描的物品一律为
     * false，无论它的要素多么少。 */
    boolean revealsNothing() {
        return !menu.slots.get(MenuDistillationEncoder.MENU_SOURCE).getItem().isEmpty() && revealedTotal == 0;
    }

    int amountFor(int index) {
        return index >= 0 && index < aspectAmounts.size() ? aspectAmounts.get(index) : 0;
    }

    /** 玩家是否已发现 {@code index} 处的要素，从而可以看见并选中它；未发现的
     * 凹槽绘制为空，并拒绝点击。 */
    boolean isRevealed(int index) {
        return index >= 0 && index < revealedWells.length && revealedWells[index];
    }

    int revealedCount() {
        return revealedTotal;
    }

    /** 被选中的要素，从这里读取而不是从槽位读取，槽位里没有任何东西；没有选中项、
     * 或该选中项不可见时为 null。 */
    @Nullable Holder<IAspect> pickedAspect() {
        int picked = pickedIndex();
        return picked >= 0 ? aspects.get(picked) : null;
    }

    int pickedAmount() {
        return amountFor(pickedIndex());
    }

    /** 哪一个要素被选中，没有则为 {@code -1}；按需求值，不做缓存，因为选中项
     * 会在没有任何东西从服务端到达的情况下改变。 */
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

    /** 一个物品的要素以及各有多少，两者一次遍历得出；数量通过
     * {@code amountsOut} 输出，因为界面打开时它每个 tick 都会运行。 */
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

        // 两侧必须以相同的方式给要素编号，因为选中项以索引传输；对索引排序
        // 而不是对要素排序，可以让每个数量与它的要素待在一起。
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
        return aspect.unwrapKey().map(k -> k.location().toString()).orElse("");
    }
}
