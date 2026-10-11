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

final class EncoderAspectTable {

    private final MenuDistillationEncoder menu;

    private final @Nullable BlockEntityDistillationEncoder encoder;

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

        for (int i = 0; i < MenuDistillationEncoder.ASPECT_SLOTS; i++) {
            aspectDisplay.setItem(i, ItemStack.EMPTY);
        }

        tracePick();
        selectedDisplay.setItem(0, ItemStack.EMPTY);
    }

    private boolean sourceIsScanned(ItemStack source) {
        return !source.isEmpty()
                && KnowledgeAccess.of(menu.owner).isResearchKnown(ScanKeys.item(source.getItem()));
    }

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

    boolean revealsNothing() {
        return !menu.slots.get(MenuDistillationEncoder.MENU_SOURCE).getItem().isEmpty() && revealedTotal == 0;
    }

    int amountFor(int index) {
        return index >= 0 && index < aspectAmounts.size() ? aspectAmounts.get(index) : 0;
    }

    boolean isRevealed(int index) {
        return index >= 0 && index < revealedWells.length && revealedWells[index];
    }

    int revealedCount() {
        return revealedTotal;
    }

    @Nullable Holder<IAspect> pickedAspect() {
        int picked = pickedIndex();
        return picked >= 0 ? aspects.get(picked) : null;
    }

    int pickedAmount() {
        return amountFor(pickedIndex());
    }

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
