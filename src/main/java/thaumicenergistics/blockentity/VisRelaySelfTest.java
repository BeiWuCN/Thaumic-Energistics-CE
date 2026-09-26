package thaumicenergistics.blockentity;

import appeng.api.parts.IPartHost;
import appeng.util.Platform;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aspect.TCAspects;
import com.leclowndu93150.thaumaturge.api.aura.IVisRelaySource;
import com.leclowndu93150.thaumaturge.api.aura.VisRelayHelper;
import com.leclowndu93150.thaumaturge.content.aura.node.BlockEntityNode;
import com.leclowndu93150.thaumaturge.content.aura.relay.BlockEntityVisRelay;
import com.leclowndu93150.thaumaturge.content.aura.relay.VisRelayNetwork;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.part.PartVisInterface;

/**
 * Answers one question with measurements instead of inference: <b>can the Arcane Assembler beside this
 * player actually draw vis along the relay chain?</b>
 *
 * <p>Off unless {@code THAUMICENERGISTICS_VIS_RELAY_TEST=true}.
 *
 * <p><b>Read-only.</b> It walks the loaded blocks around the player and prints what it finds; it builds
 * nothing, consumes nothing, and changes no block. That matters because the question it answers is about
 * a base the player has already built, and a test that rearranged anything would be measuring itself.
 *
 * <p>It exists because the chain has a shape that is easy to build wrongly and produces no error when it
 * is wrong. {@code VisRelayNetwork.drainCentivis} - the only entry point, and the same call
 * Thaumaturge's own arcane workbench makes - resolves in two steps:
 *
 * <ol>
 *   <li>{@code findRelayNear} scans the 17x17x17 around the consumer for a <b>{@code BlockEntityVisRelay}
 *       block</b>. A cable part is not one, and is never returned here.</li>
 *   <li>that relay's {@code resolveAddonSource} walks its parent chain; the last hop is the addon
 *       source's position, which for a Vis Interface is the block its cable is on.</li>
 * </ol>
 *
 * <p>And the second step is where this mod's interface gets shut out: a relay picks its own parent, and
 * {@code BlockEntityVisRelay.relink} prefers a node over an addon source <em>regardless of distance</em>, so
 * in any base with an energized node within eight blocks of the relay the chain belongs to the node and the
 * interface is never asked. The Arcane Assembler therefore asks a nearby interface directly, and the
 * interface in turn asks the relay chain it can reach - which is what makes the aspects honest at both ends.
 *
 * <p>This prints each hop, and what each interface would carry, so the answer is a number from the player's
 * own world rather than an inference from a tooltip.
 */
public final class VisRelaySelfTest {

    /** One run per server; the scan is a fixed cost and cannot change without a block being placed. */
    private static boolean hasRun;

    /** How far to look for an assembler to report on. Same reach the machine itself uses. */
    private static final int SCAN = 12;

    private VisRelaySelfTest() {}

    public static void run(PlayerEvent.PlayerLoggedInEvent event) {
        if (!"true".equalsIgnoreCase(System.getenv("THAUMICENERGISTICS_VIS_RELAY_TEST"))) {
            return;
        }
        if (hasRun || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        hasRun = true;

        ServerLevel level = player.serverLevel();
        BlockPos origin = player.blockPosition();
        ThaumicEnergistics.LOG.info("[vistest] scanning {} blocks around {} in {}", SCAN, origin, level.dimension().location());

        List<BlockPos> assemblers = find(level, origin, BlockEntityArcaneAssembler.class);
        List<BlockPos> relays = find(level, origin, BlockEntityVisRelay.class);
        List<BlockPos> interfaces = findVisInterfaces(level, origin);

        ThaumicEnergistics.LOG.info(
                "[vistest] found {} arcane assembler(s), {} vis relay block(s), {} vis interface part(s)",
                assemblers.size(), relays.size(), interfaces.size());

        for (BlockPos relayPos : relays) {
            if (!(level.getBlockEntity(relayPos) instanceof BlockEntityVisRelay relay)) {
                continue;
            }
            BlockPos parent = relay.parentPos();
            BlockEntityNodeReport node = describeParent(level, relay);
            // resolveAddonSource is the relay's own answer to "can I see an addon vis source (this mod's
            // interface) as my parent?". A node at the top of the chain wins over it in VisRelayNetwork,
            // so this is the number that says whether the interface is even in the running.
            ThaumicEnergistics.LOG.info(
                    "[vistest] relay at {} linked={} depth={} parent={} resolvesTo={} addonSource={}",
                    relayPos, relay.isLinked(), relay.depth(), parent,
                    node == null ? "NOTHING" : node.description(),
                    relay.resolveAddonSource(level));

            // <b>What the node at the end of the chain can actually give.</b> This is the number that
            // decides whether a Vis Interface carrying it is worth anything, and it is not the node's size:
            // an energized node accrues `aspectsBase.amountOf(aspect)` centivis a second (BlockEntityNode's
            // accrueCentivis, once a second) and hands out `min(allowance, stored)`, so its palette and its
            // RATE are two different things. A node holding three points of earth supplies three centivis a
            // second - 0.03 vis - however full it looks in its own GUI.
            BlockEntityNode resolved = relay.resolveSource(level);
            if (resolved != null) {
                StringBuilder palette = new StringBuilder();
                for (AspectInstance entry : resolved.getAspectsBase().entries()) {
                    if (palette.length() > 0) {
                        palette.append(", ");
                    }
                    String name = entry.aspect().unwrapKey()
                            .map(key -> key.location().getPath())
                            .orElse("?");
                    palette.append(name)
                            .append(" amount=").append(entry.amount())
                            .append(" rate=").append(resolved.centivisRate(entry.aspect())).append("c/s")
                            .append(" stored=").append(resolved.getAspects().amountOf(entry.aspect()));
                }
                ThaumicEnergistics.LOG.info(
                        "[vistest]   node at {} energized={} palette[{}]",
                        resolved.getBlockPos(), resolved.isEnergized(),
                        palette.length() == 0 ? "empty" : palette);
            }
        }

        // What each interface would carry, aspect by aspect. reserve() only reports - it asks the relay chain
        // what it would give and the network what it could pay, and takes neither - so this is read-only and
        // is the interface's own answer rather than an inference from the machine's buffer.
        //
        // Six numbers, because the point of the part is that they follow the world: a node holding three
        // aspects can answer for those three and for nothing else, whatever the network's energy says.
        for (BlockPos interfacePos : interfaces) {
            double nearest = Double.MAX_VALUE;
            for (BlockPos assemblerPos : assemblers) {
                nearest = Math.min(nearest, Math.sqrt(interfacePos.distSqr(assemblerPos)));
            }
            // The three flags Thaumaturge asks of an addon source before it will use one
            // (AddonVisRelaySources.Registration.usable): a part that answers no to any of them is
            // invisible to every relay block, however it is placed.
            StringBuilder flags = new StringBuilder();
            StringBuilder carries = new StringBuilder();
            PartVisInterface part = partAt(level, interfacePos);
            BlockPos upstream = null;
            if (part == null) {
                flags.append("part-not-found");
            } else {
                flags.append("valid=").append(part.isValid())
                        .append(" active=").append(part.isActive())
                        .append(" linked=").append(part.isLinked());
                upstream = part.upstreamPosition();
                for (ResourceKey<IAspect> primal : TCAspects.PRIMALS) {
                    IVisRelaySource.Reservation offer = part.reserve(primal, 100);
                    int amount = offer == null ? 0 : offer.amount();
                    if (offer != null) {
                        offer.close();
                    }
                    if (carries.length() > 0) {
                        carries.append(' ');
                    }
                    carries.append(primal.location().getPath()).append('=').append(amount);
                }
            }
            ThaumicEnergistics.LOG.info(
                    "[vistest] interface at {} {} upstream={} wouldCarry[{}] nearestAssembler={}",
                    interfacePos, flags, upstream,
                    carries.length() == 0 ? "not asked" : carries,
                    assemblers.isEmpty() ? "none" : String.format("%.1f", nearest));
        }

        if (assemblers.isEmpty()) {
            ThaumicEnergistics.LOG.info("[vistest] no assembler in range, so the chain was not measured from one");
        }

        // The measurement that answers the question: ask the same call the machine makes, from each
        // assembler, and see whether anything comes back. simulate=true, so nothing is actually drained.
        //
        // Every primal is asked, not just one. A relay chain resolves to a node or to an addon source, and
        // both meter per aspect; a node holds only the aspects it has been fed, so a single AER probe
        // reading zero is entirely consistent with a chain that works for the other five. The six numbers
        // together are what this base can actually pay.
        for (BlockPos assemblerPos : assemblers) {
            boolean reach = VisRelayNetwork.findRelayNear(level, assemblerPos) != null;
            StringBuilder draws = new StringBuilder();
            int total = 0;
            for (ResourceKey<IAspect> primal : TCAspects.PRIMALS) {
                // TCAspects' primals are already the ResourceKey the helper wants, not a holder.
                int drawn = VisRelayHelper.drainCentivis(level, assemblerPos, primal, 100, true);
                total += drawn;
                if (draws.length() > 0) {
                    draws.append(' ');
                }
                draws.append(primal.location().getPath()).append('=').append(drawn);
            }
            ThaumicEnergistics.LOG.info(
                    "[vistest] assembler at {}: aura={} relayBlockWithin8={} simulatedDraw[{}] total={} centivis",
                    assemblerPos,
                    com.leclowndu93150.thaumaturge.api.aura.AuraHelper.getVis(level, assemblerPos),
                    reach, draws, total);
            if (!reach) {
                ThaumicEnergistics.LOG.info(
                        "[vistest]   -> no relay BLOCK within 8. A vis interface part cannot be reached from here"
                                + " on its own: place a Thaumaturge vis relay block within 8 of the machine and"
                                + " wire the interface TO THAT BLOCK (they link up to 8 apart, 16 hops).");
            } else if (total <= 0) {
                ThaumicEnergistics.LOG.info(
                        "[vistest]   -> a relay block is in range but gave nothing for any of the six aspects."
                                + " Either the relay's parent chain reaches no node and no addon source, or the"
                                + " node at the end of it is empty.");
            } else {
                // Anything non-zero is the chain's own answer, and which aspects answered is the part worth
                // reading: a chain that pays in three aspects and not the other three is doing its job.
                ThaumicEnergistics.LOG.info(
                        "[vistest]   -> the chain answers, in the aspects listed above and no others.");
            }
        }
    }

    /**
     * What a relay's parent chain ends at, described for a human.
     *
     * <p>Separate from the drain so a relay that is linked but leads nowhere is visible as such: the drain
     * answers "did any vis come back", and this answers "<em>why</em> not", which is the half that names
     * the block to go and fix.
     */
    private static @org.jspecify.annotations.Nullable BlockEntityNodeReport describeParent(
            ServerLevel level, BlockEntityVisRelay relay) {
        var node = relay.resolveSource(level);
        if (node != null) {
            return new BlockEntityNodeReport("node", node.getBlockPos(), true);
        }
        BlockPos addon = relay.resolveAddonSource(level);
        if (addon != null) {
            return new BlockEntityNodeReport("addon source", addon, true);
        }
        return null;
    }

    /** One resolved end of a chain, for the log line. */
    private record BlockEntityNodeReport(String kind, BlockPos pos, boolean usable) {
        String description() {
            return kind + " at " + pos + (usable ? "" : " (not usable)");
        }
    }

    /** Loaded block entities of one type within {@link #SCAN} blocks, scanned coarsely. */
    /** The vis interface part mounted on the cable bus at {@code pos}, or null when there is none. */
    private static @org.jspecify.annotations.Nullable PartVisInterface partAt(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof IPartHost host)) {
            return null;
        }
        for (Direction side : Platform.DIRECTIONS_WITH_NULL) {
            if (host.getPart(side) instanceof PartVisInterface part) {
                return part;
            }
        }
        return null;
    }

    private static <T> List<BlockPos> find(ServerLevel level, BlockPos origin, Class<T> type) {
        List<BlockPos> found = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = -SCAN; x <= SCAN; x++) {
            for (int y = -SCAN; y <= SCAN; y++) {
                for (int z = -SCAN; z <= SCAN; z++) {
                    cursor.setWithOffset(origin, x, y, z);
                    if (type.isInstance(level.getBlockEntity(cursor))) {
                        found.add(cursor.immutable());
                    }
                }
            }
        }
        return found;
    }

    /**
     * Blocks carrying a Vis Interface part.
     *
     * <p>A part is not a block entity, so it cannot be found by type - it has to be asked of the cable bus
     * it is mounted on, one face at a time. Reported separately from the relay blocks because the two are
     * what a player confuses: seeing an interface beside the machine <em>feels</em> like enough, and this
     * line is where the world says whether it is.
     */
    private static List<BlockPos> findVisInterfaces(ServerLevel level, BlockPos origin) {
        List<BlockPos> found = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = -SCAN; x <= SCAN; x++) {
            for (int y = -SCAN; y <= SCAN; y++) {
                for (int z = -SCAN; z <= SCAN; z++) {
                    cursor.setWithOffset(origin, x, y, z);
                    if (!(level.getBlockEntity(cursor) instanceof IPartHost host)) {
                        continue;
                    }
                    for (Direction side : Platform.DIRECTIONS_WITH_NULL) {
                        if (host.getPart(side) instanceof PartVisInterface) {
                            found.add(cursor.immutable());
                            break;
                        }
                    }
                }
            }
        }
        return found;
    }
}
