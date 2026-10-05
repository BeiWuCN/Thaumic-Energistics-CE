package thaumicenergistics_ce.menu;

import appeng.core.definitions.AEItems;
import com.leclowndu93150.thaumaturge.api.aspect.AspectIndexAccess;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.capability.KnowledgeAccess;
import com.leclowndu93150.thaumaturge.api.research.pool.AspectPoolAccess;
import com.leclowndu93150.thaumaturge.api.research.scan.ScanKeys;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.blockentity.BlockEntityDistillationEncoder;
import thaumicenergistics_ce.init.ModMenuTypes;
import thaumicenergistics_ce.menu.slot.AspectSelectSlot;
import thaumicenergistics_ce.menu.slot.MachineOutputSlot;
import thaumicenergistics_ce.menu.slot.TemplateSlot;
import thaumicenergistics_ce.net.DistillationEncoderReceiver;
import thaumicenergistics_ce.util.ThELog;

/**
 * The Distillation Encoder's menu: the item, its aspects, the picked one and the pattern wells.
 * <ul>
 *   <li>Both sides <em>derive</em> the aspect row from the synced item, so the two cannot disagree.
 *   <li>The pick is not synced: an instruction to the server, mirrored only to draw the highlight.
 * </ul>
 */
public class MenuDistillationEncoder extends AbstractContainerMenu implements DistillationEncoderReceiver {

    public static final int PLAYER_SLOTS = 36;

    public static final int IDX_SOURCE = 0;

    public static final int IDX_BLANK = 1;

    public static final int IDX_ENCODED = 2;

    public static final int IDX_ASPECT_START = 3;

    public static final int ASPECT_SLOTS = BlockEntityDistillationEncoder.MAX_ASPECTS;

    public static final int IDX_SELECTED = IDX_ASPECT_START + ASPECT_SLOTS;

    /** Menu index of the row's first well: {@link #PLAYER_SLOTS} above its container index; clicks and
     * {@code quickMoveStack} number slots differently. */
    public static final int MENU_ASPECT_START = PLAYER_SLOTS + IDX_ASPECT_START;
    public static final int MENU_SELECTED = PLAYER_SLOTS + IDX_SELECTED;

    public static final int MENU_SOURCE = PLAYER_SLOTS + IDX_SOURCE;

    public static final int MENU_BLANK = PLAYER_SLOTS + IDX_BLANK;
    public static final int MENU_ENCODED = PLAYER_SLOTS + IDX_ENCODED;

    // From the reference build's screen art.
    private static final int SOURCE_X = 15;
    private static final int SOURCE_Y = 69;
    private static final int ASPECTS_X = 65;
    private static final int ASPECTS_Y = 24;
    private static final int ASPECT_PITCH = 18;
    private static final int SELECTED_X = 116;
    private static final int SELECTED_Y = 69;
    private static final int BLANK_X = 146;
    private static final int BLANK_Y = 75;
    private static final int ENCODED_X = 146;
    private static final int ENCODED_Y = 113;

    private static final int INV_X = 8;
    private static final int INV_Y = 150;
    private static final int HOTBAR_Y = 208;
    private static final int PITCH = 18;

    private List<Holder<IAspect>> aspects = List.of();

    private List<Integer> aspectAmounts = List.of();

    private boolean[] revealedWells = new boolean[ASPECT_SLOTS];

    private int revealedTotal;

    private int tracedPick = -1;

    private static final boolean TRACE = System.getenv("THAUMICENERGISTICS_ENCODER_TRACE") != null;

    private ItemStack tracedSource = ItemStack.EMPTY;

    private int tracedRevealed = -1;

    private ItemStack lastSourceItem = ItemStack.EMPTY;



    private final Player owner;

    private int localSelection = -1;

    private final @Nullable BlockEntityDistillationEncoder encoder;

    private final SimpleContainer aspectDisplay = new SimpleContainer(ASPECT_SLOTS);

    private final SimpleContainer selectedDisplay = new SimpleContainer(1);

    public MenuDistillationEncoder(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInventory, (BlockEntityDistillationEncoder) null);
    }

    public MenuDistillationEncoder(
            int containerId, Inventory playerInventory, @Nullable BlockEntityDistillationEncoder encoder) {
        super(ModMenuTypes.DISTILLATION_ENCODER.get(), containerId);
        this.encoder = encoder;
        this.owner = playerInventory.player;
        Container source = encoder == null ? new SimpleContainer(BlockEntityDistillationEncoder.SLOT_COUNT) : encoder.getInventory();

        // 1. The player's inventory, first as everywhere else in this mod.
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9, INV_X + column * PITCH, INV_Y + row * PITCH));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, INV_X + column * PITCH, HOTBAR_Y));
        }

        // 2. The machine. The source well is a template, not a deposit - see TemplateSlot.
        addSlot(new TemplateSlot(source, BlockEntityDistillationEncoder.SLOT_SOURCE, SOURCE_X, SOURCE_Y));
        addSlot(new Slot(source, BlockEntityDistillationEncoder.SLOT_BLANK, BLANK_X, BLANK_Y));
        // Written by the machine, taken by the player, never placed - see MachineOutputSlot.
        addSlot(new MachineOutputSlot(source, BlockEntityDistillationEncoder.SLOT_ENCODED, ENCODED_X, ENCODED_Y));

        // 3. The aspect row and the picked aspect: views written by refreshAspects, never by the player.
        for (int i = 0; i < ASPECT_SLOTS; i++) {
            // Down the panel, not across it.
            addSlot(new AspectSelectSlot(aspectDisplay, i, ASPECTS_X, ASPECTS_Y + i * ASPECT_PITCH, i, this));
        }
        addSlot(new AspectSelectSlot(selectedDisplay, 0, SELECTED_X, SELECTED_Y, -1, this));

        refreshAspects();
    }

    // ------------------------------------------------------------------
    // The aspect row
    // ------------------------------------------------------------------

    /** Brings the row up to date if the source item changed. Nothing tells a client menu to derive it
     * ({@link #broadcastChanges} runs from the server's tick), so the screen calls this before drawing. */
    public void ensureAspects() {
        if (!ItemStack.matches(slots.get(MENU_SOURCE).getItem(), lastSourceItem)) {
            refreshAspects();
        }
    }

    private void refreshAspects() {
        List<Holder<IAspect>> found;
        List<Integer> foundAmounts = new ArrayList<>();
        if (encoder != null) {
            found = encoder.availableAspects();
            for (Holder<IAspect> aspect : found) {
                foundAmounts.add((int) Math.min(Integer.MAX_VALUE, encoder.yieldFor(aspect)));
            }
        } else if (!slots.isEmpty()) {
            found = deriveAspects(slots.get(PLAYER_SLOTS + IDX_SOURCE).getItem(), foundAmounts);
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
        ItemStack source = slots.get(MENU_SOURCE).getItem();
        lastSourceItem = source.copy();
        boolean itemScanned = sourceIsScanned(source);
        boolean[] revealedWells = new boolean[ASPECT_SLOTS];
        int revealedTotal = 0;
        for (int i = 0; i < ASPECT_SLOTS && i < aspects.size(); i++) {
            if (itemScanned && AspectPoolAccess.isDiscovered(owner, aspects.get(i))) {
                revealedWells[i] = true;
                revealedTotal++;
            }
        }
        traceSource(source, itemScanned, revealedTotal);
        this.revealedWells = revealedWells;
        this.revealedTotal = revealedTotal;

        // Left empty on purpose, as the reference build leaves them: the screen draws the icon, its amount
        // and the picked frame; a stack here would render an item under the aspect icon.
        for (int i = 0; i < ASPECT_SLOTS; i++) {
            aspectDisplay.setItem(i, ItemStack.EMPTY);
        }

        tracePick();
        // The picked well is emptied too, and drawn by the screen like the others.
        selectedDisplay.setItem(0, ItemStack.EMPTY);
    }

    private boolean sourceIsScanned(ItemStack source) {
        return !source.isEmpty() && KnowledgeAccess.of(owner).isResearchKnown(ScanKeys.item(source.getItem()));
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
        int raw = encoder != null ? encoder.selectedAspectIndex() : localSelection;
        if (!TRACE || raw == tracedPick) {
            return;
        }
        tracedPick = raw;
        String why = raw < 0
                ? "nothing picked"
                : raw >= aspects.size()
                        ? "refused: no aspect at " + raw
                        : !isAspectRevealed(raw) ? "refused: aspect " + raw + " is not revealed" : "accepted";
        ThELog.LOG.info(
                "[encoder] pick {} of {} -> {} ({}), {} side", raw, aspects.size(), pickedIndex(), why,
                encoder != null ? "server" : "client");
    }

    /** True when the source well holds something but nothing can be offered from it; a scanned item is
     * false, however few aspects it has. */
    public boolean sourceRevealsNothing() {
        return !slots.get(MENU_SOURCE).getItem().isEmpty() && revealedTotal == 0;
    }

    public int aspectAmountFor(int index) {
        return index >= 0 && index < aspectAmounts.size() ? aspectAmounts.get(index) : 0;
    }

    /** Whether the player has discovered the aspect at {@code index}, and so may see and pick it; an
     * undiscovered well is drawn as nothing and refuses clicks. */
    public boolean isAspectRevealed(int index) {
        return index >= 0 && index < revealedWells.length && revealedWells[index];
    }

    public int revealedAspectCount() {
        return revealedTotal;
    }

    /** The picked aspect, read here and not from the slots, which hold nothing; null when none is
     * picked or the pick may not be seen. */
    public @Nullable Holder<IAspect> pickedAspect() {
        int picked = pickedIndex();
        return picked >= 0 ? aspects.get(picked) : null;
    }

    public int pickedAmount() {
        return aspectAmountFor(pickedIndex());
    }

    /** Which aspect is picked, or {@code -1} for none; worked out on demand, not cached, since the pick
     * changes without anything arriving from the server. */
    private int pickedIndex() {
        int picked = encoder != null ? encoder.selectedAspectIndex() : localSelection;
        return picked >= 0 && picked < aspects.size() && isAspectRevealed(picked) ? picked : -1;
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
            if (entry.amount() > 0 && found.size() < ASPECT_SLOTS) {
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

    public List<Holder<IAspect>> aspects() {
        return aspects;
    }

    public int aspectCount() {
        return aspects.size();
    }

    public int localSelection() {
        return localSelection;
    }

    // ------------------------------------------------------------------
    // Actions from the screen
    // ------------------------------------------------------------------

    @Override
    public void selectAspect(int index) {
        if (index < -1 || index >= aspects.size()) {
            return;
        }
        // Refused here and not only in the screen: an action payload arrives through this path too, and a
        // hand-assembled click must not select an undiscovered aspect.
        if (index >= 0 && !isAspectRevealed(index)) {
            return;
        }
        localSelection = index;
        if (encoder != null) {
            encoder.setSelectedAspect(index);
            refreshAspects();
        }
    }

    @Override
    public void encode() {
        if (encoder != null) {
            encoder.encode();
            refreshAspects();
        }
    }

    @Override
    public void applySourceTemplate(ItemStack stack) {
        ItemStack wanted = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        slots.get(MENU_SOURCE).set(wanted);
        localSelection = -1;
        refreshAspects();
    }

    public void requestSourceTemplate(ItemStack stack) {
        ItemStack wanted = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        slots.get(MENU_SOURCE).set(wanted);
        localSelection = -1;
        refreshAspects();
        MenuNetwork.sendEncoderSource(containerId, wanted);
    }

    @Override
    public void insertBlankFromInventory(Player player) {
        if (!slots.get(MENU_BLANK).getItem().isEmpty()) {
            return;
        }
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (AEItems.BLANK_PATTERN.is(stack)) {
                ItemStack one = stack.copyWithCount(1);
                stack.shrink(1);
                inventory.setChanged();
                slots.get(MENU_BLANK).set(one);
                refreshAspects();
                return;
            }
        }
    }

    @Override
    public int containerId() {
        return containerId;
    }

    public boolean canEncode() {
        if (slots.get(MENU_SOURCE).getItem().isEmpty()) {
            return false;
        }
        if (pickedIndex() < 0) {
            return false;
        }
        ItemStack blank = slots.get(MENU_BLANK).getItem();
        if (blank.isEmpty() || !AEItems.BLANK_PATTERN.is(blank)) {
            return false;
        }
        return slots.get(MENU_ENCODED).getItem().isEmpty();
    }

    public void sendAction(int action, int value) {
        MenuNetwork.sendEncoderAction(containerId, action, value);
    }

    @Override
    public void broadcastChanges() {
        // Runs on the server only - a client never calls this; ensureAspects keeps its copy current
        // instead.
        ensureAspects();
        super.broadcastChanges();
    }

    // ------------------------------------------------------------------
    // Clicks
    // ------------------------------------------------------------------

    /** Intercepts clicks on the aspect row and the picked-aspect display: clicking one means 'use this
     * aspect', and falling through to vanilla would let a player pull a phantom item out of a display. */
    @Override
    public void clicked(int slotId, int dragType, ClickType clickType, Player player) {
        // The row is re-derived first, so the decision is made against it as it is now.
        ensureAspects();
        // Menu indices, not container ones: slotId indexes this menu's list, players' slots first. The
        // source well is handled here too, as TemplateSlot refuses both ways and so cannot be emptied.
        if (slotId == MENU_SOURCE) {
            if (player.level().isClientSide) {
                ItemStack carried = getCarried();
                requestSourceTemplate(carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1));
            }
            return;
        }
        if (slotId >= MENU_ASPECT_START && slotId < MENU_ASPECT_START + ASPECT_SLOTS) {
            int index = slotId - MENU_ASPECT_START;
            // Nothing is drawn for an undiscovered well, so a click where nothing is drawn must not pick.
            if (index < aspects.size() && isAspectRevealed(index)) {
                localSelection = index;
                if (player.level().isClientSide) {
                    sendAction(MenuNetwork.ACTION_SELECT, index);
                } else if (encoder != null) {
                    encoder.setSelectedAspect(index);
                }
            }
            return;
        }
        if (slotId == MENU_SELECTED) {
            // Clicking the picked aspect clears it.
            localSelection = -1;
            if (player.level().isClientSide) {
                sendAction(MenuNetwork.ACTION_SELECT, -1);
            } else if (encoder != null) {
                encoder.setSelectedAspect(-1);
            }
            return;
        }
        super.clicked(slotId, dragType, clickType, player);
    }

    private static final int MACHINE_START = PLAYER_SLOTS;
    private static final int MACHINE_END = PLAYER_SLOTS + 3;

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index >= MACHINE_START && index < MACHINE_END) {
            if (!moveItemStackTo(stack, 0, PLAYER_SLOTS, true)) {
                return ItemStack.EMPTY;
            }
        } else if (AEItems.BLANK_PATTERN.is(stack)) {
            if (!moveItemStackTo(stack, PLAYER_SLOTS + IDX_BLANK, PLAYER_SLOTS + IDX_BLANK + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (!moveItemStackTo(stack, PLAYER_SLOTS + IDX_SOURCE, PLAYER_SLOTS + IDX_SOURCE + 1, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        if (encoder == null) {
            return true;
        }
        var level = encoder.getLevel();
        var pos = encoder.getBlockPos();
        return level != null
                && level.getBlockEntity(pos) == encoder
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }
}
