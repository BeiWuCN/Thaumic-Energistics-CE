package thaumicenergistics_ce.selftest;

import net.neoforged.neoforge.common.NeoForge;
import thaumicenergistics_ce.init.SelfTestProvider;
import thaumicenergistics_ce.blockentity.assembler.AssemblerScanCostSelfTest;
import thaumicenergistics_ce.blockentity.occultmonitor.OccultMonitorPulseSelfTest;
import thaumicenergistics_ce.init.capability.MachineItemBandSelfTest;
import thaumicenergistics_ce.part.EssentiaTransportViewSelfTest;

/**
 * The self-tests' half of the seam: this source set is not in the release, so the mod reaches these
 * listeners through {@link SelfTestProvider} and a build without them simply does not call them.
 */
public final class SelfTestBootstrap implements SelfTestProvider {
    @Override
    public void registerSelfTests() {
        NeoForge.EVENT_BUS.addListener(RecipeSelfTest::run);
        NeoForge.EVENT_BUS.addListener(EssentiaSelfTest::run);
        // Waits for the tick that can see Thaumaturge's aspect index, which is published a hop late.
        NeoForge.EVENT_BUS.addListener(EssentiaSelfTest::onServerTick);
        NeoForge.EVENT_BUS.addListener(GearSelfTest::run);
        NeoForge.EVENT_BUS.addListener(MenuSelfTest::run);
        NeoForge.EVENT_BUS.addListener(ResearchSelfTest::run);
        NeoForge.EVENT_BUS.addListener(InscriberSelfTest::run);
        // The Distillation Encoder: the mod's other container whose slots a save can rearrange.
        NeoForge.EVENT_BUS.addListener(EncoderSelfTest::run);
        // Same wait as the essentia battery: the encoder reads the same aspect index.
        NeoForge.EVENT_BUS.addListener(EncoderSelfTest::onServerTick);
        // Runs on login against block entities never added to a level; builds nothing. It answers the
        // server-starting event too, so a headless gate sees these checks without a player.
        NeoForge.EVENT_BUS.addListener(AssemblerCraftSelfTest::run);
        NeoForge.EVENT_BUS.addListener(AssemblerCraftSelfTest::onServerStarted);
        // The cell workbench's partition: a mark has to reach the cell item, survive a save, and then
        // filter it. Nothing a client writes to the grid is ever sent - see PartitionWellPayload.
        NeoForge.EVENT_BUS.addListener(CellPartitionSelfTest::onServerStarted);
        // Read-only check that an assembler can reach a relay block; a lone interface cannot.
        NeoForge.EVENT_BUS.addListener(VisRelaySelfTest::run);
        // The payload codecs: a field dropped while the records moved packages compiles and only shows
        // up as a client drawing something the server never sent.
        NeoForge.EVENT_BUS.addListener(NetworkSelfTest::run);
        // Every synced number, in and out: the save tag, the packet, the menu reading table. A figure
        // that goes out and comes back changed shows up nowhere else until a player reads it.
        NeoForge.EVENT_BUS.addListener(SyncSelfTest::run);
        // The port an essentia bus shows a pipe: a pipe asks only for TRANSPORT, so a bus that
        // answers nothing is a bus no tube can reach - invisible until something is placed by hand.
        NeoForge.EVENT_BUS.addListener(EssentiaTransportViewSelfTest::run);
        // What a pipe may reach against what a broken machine gives back: the source well of the
        // distillation encoder is a name JEI writes for free, so a band that reached it mints items.
        NeoForge.EVENT_BUS.addListener(MachineItemBandSelfTest::run);
        // The assembler's own cube of 4,913 block entity lookups: timed before anyone decides to
        // shrink it or to have the vis interface announce itself instead.
        NeoForge.EVENT_BUS.addListener(AssemblerScanCostSelfTest::run);
        // The pulse a finished ritual leaves on the machine's block: 15 to a neighbour, and gone by
        // itself a tick later. A stuck signal is what a player cannot fix, so it waits and looks.
        NeoForge.EVENT_BUS.addListener(OccultMonitorPulseSelfTest::run);
        NeoForge.EVENT_BUS.addListener(OccultMonitorPulseSelfTest::onServerTick);
        // What the wireless arcane terminal writes when it is paired: the menu test needs a player to log
        // in, and this half - the item's own tag - is exactly the half a headless server can hold.
        NeoForge.EVENT_BUS.addListener(WirelessArcaneBindingSelfTest::run);
        // The ME interface's access card: which aspects a mark lets in, and the rates a round moves them
        // at. Nothing here needs a level, which is the half a headless gate can check.
        NeoForge.EVENT_BUS.addListener(EssentiaInterfaceSelfTest::run);
        // The wireless arcane terminal's access card: the AE2 registration, the item's two upgrade slots
        // and the card that has to ride on the item's own stack. No player, no screen, no live grid.
        NeoForge.EVENT_BUS.addListener(WirelessArcaneEssentiaSelfTest::run);
        // The vis connection card: whether the card rides the wireless arcane terminal's own slots, and
        // whether the aura path answers the same across the two passes one craft makes.
        NeoForge.EVENT_BUS.addListener(VisConnectionSelfTest::run);
    }
}
