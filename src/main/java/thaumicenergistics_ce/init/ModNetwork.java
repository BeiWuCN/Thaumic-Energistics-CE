package thaumicenergistics_ce.init;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import thaumicenergistics_ce.network.ArcaneCraftCostPayload;
import thaumicenergistics_ce.network.ArcaneUnbindPayload;
import thaumicenergistics_ce.network.EncoderActionPayload;
import thaumicenergistics_ce.network.EncoderSourcePayload;
import thaumicenergistics_ce.network.EssentiaBusConfigPayload;
import thaumicenergistics_ce.network.EssentiaDepositPayload;
import thaumicenergistics_ce.network.EssentiaFillPayload;
import thaumicenergistics_ce.network.EssentiaInterfaceMarkPayload;
import thaumicenergistics_ce.network.GolemBackpackPayload;
import thaumicenergistics_ce.network.InscriberGridFillPayload;
import thaumicenergistics_ce.network.InscriberGridPayload;
import thaumicenergistics_ce.network.PartitionWellPayload;

/**
 * Network registration. The Knowledge Inscriber's button needs no payload, because vanilla's
 * own menu-button packet carries the id, but the inscriber's crafting grid does: it is a ghost
 * grid the client fills, and only the server can turn it into a recipe.
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
        // A mark dropped onto an ME interface's own config or storage row. Serverbound for the bus above's
        // reason, and it is refused server-side unless the access card is in that interface.
        registrar.playToServer(
                EssentiaInterfaceMarkPayload.TYPE,
                EssentiaInterfaceMarkPayload.CODEC,
                (payload, context) -> payload.handle(context.player()));
        // A cell workbench partition well set from JEI. Serverbound like the bus above, and for a second
        // reason: AE2's grid packet reaches a fake slot only through AEBaseMenu - see PartitionWellPayload.
        registrar.playToServer(
                PartitionWellPayload.TYPE,
                PartitionWellPayload.CODEC,
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
                (payload, context) -> payload.handleOnClient(context.player()));
        // Server to client, and otherwise the only one that travels that way: an arcane recipe's vis cost
        // can only be worked out on the server, and the screen has to draw it.
        registrar.playToClient(
                ArcaneCraftCostPayload.TYPE,
                ArcaneCraftCostPayload.CODEC,
                (payload, context) -> payload.handleOnClient(context.player()));
        // Forgetting the paired terminal: the sneak left-click that asks for it happens on the client,
        // which is the only side that sees a click into thin air - see ArcaneUnbindPayload.
        registrar.playToServer(
                ArcaneUnbindPayload.TYPE,
                ArcaneUnbindPayload.CODEC,
                (payload, context) -> payload.handle(context.player()));
    }
}
