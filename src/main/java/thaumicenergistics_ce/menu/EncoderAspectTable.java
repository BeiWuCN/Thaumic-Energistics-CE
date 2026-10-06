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

/** The encoder's aspect row: what the source item offers, how much of each, which wells the player may
 * see and which one is picked. Both sides derive it from the synced item, so the two cannot disagree. */
final class EncoderAspectTable {

    private final MenuDistillationEncoder menu;

    private final @Nullable BlockEntityDistillationEncoder encoder;

    /** Views written by {@link #refresh}, never by the player. */
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

    /** Brings the row up to date if the source item changed. Nothing tells a client menu to derive it
     * ({@link MenuDistillationEncoder#broadcastChanges} runs from the server's tick), so the screen calls
     * this before drawing. */
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

        // A flag per well, not a shorter list: the pick travels as an index into the row. Both tests are
        // needed - item scanned and aspect discovered - as Thaumaturge counts every primal as known.
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

        // Left empty on purpose, as the reference build leaves them: the screen draws the icon, its amount
        // and the picked frame; a stack here would render an item under the aspect icon.
        for (int i = 0; i < MenuDistillationEncoder.ASPECT_SLOTS; i++) {
            aspectDisplay.setItem(i, ItemStack.EMPTY);
        }

        tracePick();
        // The picked well is emptied too, and drawn by the screen like the others.
        selectedDisplay.setItem(0, ItemStack.EMPTY);
    }

    private boolean sourceIsScanned(ItemStack source) {
        return !source.isEmpty()
                && KnowledgeAccess.of(menu.owner).isResearchKnown(ScanKeys.item(source.getItem()));
    }

    /** Logs what the reveal tests answered, once per source item, when the switch is set: 'the aspects
     * do not show' has three causes that look alike. */
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

    /** Logs a change of pick and why one was refused: either way the well draws nothing. */
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

    /** True when the source well holds something but nothing can be offered from it; a scanned item is
     * false, however few aspects it has. */
    boolean revealsNothing() {
        return !menu.slots.get(MenuDistillationEncoder.MENU_SOURCE).getItem().isEmpty() && revealedTotal == 0;
    }

    int amountFor(int index) {
        return index >= 0 && index < aspectAmounts.size() ? aspectAmounts.get(index) : 0;
    }

    /** Whether the player has discovered the aspect at {@code index}, and so may see and pick it; an
     * undiscovered well is drawn as nothing and refuses clicks. */
    boolean isRevealed(int index) {
        return index >= 0 && index < revealedWells.length && revealedWells[index];
    }

    int revealedCount() {
        return revealedTotal;
    }

    /** The picked aspect, read here and not from the slots, which hold nothing; null when none is
     * picked or the pick may not be seen. */
    @Nullable Holder<IAspect> pickedAspect() {
        int picked = pickedIndex();
        return picked >= 0 ? aspects.get(picked) : null;
    }

    int pickedAmount() {
        return amountFor(pickedIndex());
    }

    /** Which aspect is picked, or {@code -1} for none; worked out on demand, not cached, since the pick
     * changes without anything arriving from the server. */
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

    /** The aspects of an item and how much of each it carries, walked once for both; the amounts come out
     * through {@code amountsOut} since this runs every tick while the screen is open. */
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

        // Both sides must number the aspects identically, as the pick travels as an index; sorting the
        // indices rather than the aspects keeps each amount beside its aspect.
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
