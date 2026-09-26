package thaumicenergistics.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Network registration.
 *
 * <p>The Knowledge Inscriber's button still travels in vanilla's own menu-button packet - the click only
 * needs an id, and the menu already carries it. What does need a payload is the inscriber's crafting
 * grid, which is a ghost grid: the client fills it, and the server is the only side that can turn it into
 * a recipe.
 */
public final class ModNetwork {

    private ModNetwork() {}

    /** The protocol version, bumped whenever a payload's shape changes. */
    private static final String VERSION = "1";

    public static void register(net.neoforged.bus.api.IEventBus modBus) {
        modBus.addListener(ModNetwork::onRegisterPayloads);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToServer(
                InscriberGridPayload.TYPE,
                InscriberGridPayload.CODEC,
                (payload, context) -> payload.handle(context.player()));
        // The whole grid at once, for loading a stored recipe or a JEI transfer. One message rather than nine,
        // because nine separate grid writes make the machine resolve eight grids that are neither recipe and
        // the screen draw all of them - see InscriberGridFillPayload.
        registrar.playToServer(
                InscriberGridFillPayload.TYPE,
                InscriberGridFillPayload.CODEC,
                (payload, context) -> payload.handle(context.player()));
        // The essentia terminal moves container contents rather than items, which AE2's own terminal
        // packets cannot express - see each payload for why.
        registrar.playToServer(
                EssentiaDepositPayload.TYPE,
                EssentiaDepositPayload.CODEC,
                (payload, context) -> payload.handle(context.player()));
        registrar.playToServer(
                EssentiaFillPayload.TYPE,
                EssentiaFillPayload.CODEC,
                (payload, context) -> payload.handle(context.player()));
        // A bus config slot set from JEI. Serverbound and not a slot write, because an essentia key is not an
        // item and AE2's ghost-slot route only carries items - see EssentiaBusConfigPayload.
        registrar.playToServer(
                EssentiaBusConfigPayload.TYPE,
                EssentiaBusConfigPayload.CODEC,
                (payload, context) -> payload.handle(context.player()));
        // The distillation encoder's screen has no item slot for its aspect wells - an aspect is not an
        // item - so picking one and asking for a pattern both travel as instructions.
        registrar.playToServer(
                EncoderActionPayload.TYPE,
                EncoderActionPayload.CODEC,
                (payload, context) -> payload.handle(context.player()));
        // And the source template, which is the one encoder instruction whose argument is neither a number
        // nor derivable on the server - see EncoderSourcePayload.
        registrar.playToServer(
                EncoderSourcePayload.TYPE,
                EncoderSourcePayload.CODEC,
                (payload, context) -> payload.handle(context.player()));
        // A golem's backpack, told to the players watching the golem. What the backpack is lives in the
        // golem's persistent data, which vanilla does not sync, and the backpack has to be drawn - see
        // GolemBackpackPayload.
        registrar.playToClient(
                GolemBackpackPayload.TYPE,
                GolemBackpackPayload.CODEC,
                GolemBackpackPayload::handle);
        // Server to client, and otherwise the only one that travels that way: an arcane recipe's vis cost
        // can only be worked out on the server, and the screen has to draw it.
        registrar.playToClient(
                ArcaneCraftCostPayload.TYPE,
                ArcaneCraftCostPayload.CODEC,
                (payload, context) -> thaumicenergistics.client.ScreenArcaneCraftingTerminal
                        .acceptCost(payload));
    }
}
