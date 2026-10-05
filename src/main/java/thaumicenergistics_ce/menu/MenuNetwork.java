package thaumicenergistics_ce.menu;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumicenergistics_ce.network.EncoderActionPayload;
import thaumicenergistics_ce.network.EncoderSourcePayload;
import thaumicenergistics_ce.network.InscriberGridFillPayload;
import thaumicenergistics_ce.network.InscriberGridPayload;

/**
 * The four requests a menu sends to the server, built in one place.
 * <ul>
 *   <li>A menu is the container's client half, so it asks; this is the only place a menu names a payload.
 *   <li>Here and not in {@code net}, which is what the two sides agree on and so must not know what a
 *       menu decided.
 * </ul>
 */
public final class MenuNetwork {

    private MenuNetwork() {}

    /** Picks the aspect at the sent value, or clears the pick when it is negative. */
    public static final int ACTION_SELECT = EncoderActionPayload.ACTION_SELECT;

    /** Writes one pattern from the current source item, aspect and blank. */
    public static final int ACTION_ENCODE = EncoderActionPayload.ACTION_ENCODE;

    /** Moves one blank into the blank well for a drag, which must not conjure one: the encode spends it. */
    public static final int ACTION_INSERT_BLANK = EncoderActionPayload.ACTION_INSERT_BLANK;

    /** One cell of the Inscriber's ghost grid; the stack arrives already trimmed to one item. */
    public static void sendInscriberGrid(int containerId, int cell, ItemStack stack) {
        PacketDistributor.sendToServer(new InscriberGridPayload(containerId, cell, stack));
    }

    /** The whole Inscriber grid in one write: a payload per cell would re-resolve against a half grid. */
    public static void sendInscriberGridFill(int containerId, List<ItemStack> cells) {
        PacketDistributor.sendToServer(new InscriberGridFillPayload(containerId, List.copyOf(cells)));
    }

    /** Which aspect the Distillation Encoder should be working from. */
    public static void sendEncoderSource(int containerId, ItemStack stack) {
        PacketDistributor.sendToServer(new EncoderSourcePayload(containerId, stack.copy()));
    }

    /** An Encoder button: select / encode / insert, encoded as one int plus its value. */
    public static void sendEncoderAction(int containerId, int action, int value) {
        PacketDistributor.sendToServer(new EncoderActionPayload(containerId, action, value));
    }
}
