package com.beiwu.thaumicenergistics_ce.registry;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import com.beiwu.thaumicenergistics_ce.network.ArcaneCraftCostPayload;
import com.beiwu.thaumicenergistics_ce.network.InscriberCostPayload;
import com.beiwu.thaumicenergistics_ce.network.ArcaneUnbindPayload;
import com.beiwu.thaumicenergistics_ce.network.EncoderActionPayload;
import com.beiwu.thaumicenergistics_ce.network.EncoderSourcePayload;
import com.beiwu.thaumicenergistics_ce.network.EssentiaDepositPayload;
import com.beiwu.thaumicenergistics_ce.network.EssentiaFillPayload;
import com.beiwu.thaumicenergistics_ce.network.EssentiaBusConfigPayload;
import com.beiwu.thaumicenergistics_ce.network.GolemBackpackPayload;
import com.beiwu.thaumicenergistics_ce.network.InscriberGridFillPayload;
import com.beiwu.thaumicenergistics_ce.network.InscriberGridPayload;
import com.beiwu.thaumicenergistics_ce.network.PartitionWellPayload;

public final class TECENetwork {

    private TECENetwork() {}

    private static final String VERSION = "1";

    public static void register(IEventBus modBus) {
        modBus.addListener(TECENetwork::onRegisterPayloads);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToServer(
                InscriberGridPayload.TYPE,
                InscriberGridPayload.CODEC,
                (payload, context) -> payload.handle(context.player()));
        registrar.playToServer(
                InscriberGridFillPayload.TYPE,
                InscriberGridFillPayload.CODEC,
                (payload, context) -> payload.handle(context.player()));
        registrar.playToServer(
                EssentiaDepositPayload.TYPE,
                EssentiaDepositPayload.CODEC,
                (payload, context) -> payload.handle(context.player()));
        registrar.playToServer(
                EssentiaFillPayload.TYPE,
                EssentiaFillPayload.CODEC,
                (payload, context) -> payload.handle(context.player()));
        registrar.playToServer(
                EssentiaBusConfigPayload.TYPE,
                EssentiaBusConfigPayload.CODEC,
                (payload, context) -> payload.handle(context.player()));
        registrar.playToServer(
                PartitionWellPayload.TYPE,
                PartitionWellPayload.CODEC,
                (payload, context) -> payload.handle(context.player()));
        registrar.playToServer(
                EncoderActionPayload.TYPE,
                EncoderActionPayload.CODEC,
                (payload, context) -> payload.handle(context.player()));
        registrar.playToServer(
                EncoderSourcePayload.TYPE,
                EncoderSourcePayload.CODEC,
                (payload, context) -> payload.handle(context.player()));
        registrar.playToClient(GolemBackpackPayload.TYPE, GolemBackpackPayload.CODEC);
        registrar.playToClient(ArcaneCraftCostPayload.TYPE, ArcaneCraftCostPayload.CODEC);
        registrar.playToClient(InscriberCostPayload.TYPE, InscriberCostPayload.CODEC);
        registrar.playToServer(
                ArcaneUnbindPayload.TYPE,
                ArcaneUnbindPayload.CODEC,
                (payload, context) -> payload.handle(context.player()));
    }
}
