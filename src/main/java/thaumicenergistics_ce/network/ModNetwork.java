package thaumicenergistics_ce.network;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Network registration.
 * <ul>
 * <li>The Knowledge Inscriber's button needs no payload: vanilla's own menu-button packet carries the id.
 * <li>The inscriber's crafting grid does: it is a ghost grid the client fills, and only the server can
 * turn it into a recipe.
 * </ul>
 */
public final class ModNetwork {

    private ModNetwork() {}

    private static final String VERSION = "1";

    public static void register(IEventBus modBus) {
        modBus.addListener(ModNetwork::onRegisterPayloads);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToServer(
                InscriberGridPayload.TYPE,
                InscriberGridPayload.CODEC,
                (payload, context) -> payload.handle(context.player()));
        // The whole grid at once, for a stored recipe or a JEI transfer: nine writes would leave the
        // machine resolving eight grids that are neither recipe nor drawn - see InscriberGridFillPayload.
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
        // A golem's backpack, told to the players watching the golem: what it is lives in the
        // golem's persistent data, which vanilla does not sync, so it still has to be drawn.
        registrar.playToClient(
                GolemBackpackPayload.TYPE,
                GolemBackpackPayload.CODEC,
                GolemBackpackPayload::handle);
        // Server to client, and otherwise the only one that travels that way: an arcane recipe's vis cost
        // can only be worked out on the server, and the screen has to draw it.
        registrar.playToClient(
                ArcaneCraftCostPayload.TYPE,
                ArcaneCraftCostPayload.CODEC,
                (payload, context) -> payload.handleOnClient(context.player()));
    }
}
