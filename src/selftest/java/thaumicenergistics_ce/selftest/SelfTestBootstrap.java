package thaumicenergistics_ce.selftest;

import net.neoforged.neoforge.common.NeoForge;
import thaumicenergistics_ce.init.SelfTestProvider;

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
    }
}
