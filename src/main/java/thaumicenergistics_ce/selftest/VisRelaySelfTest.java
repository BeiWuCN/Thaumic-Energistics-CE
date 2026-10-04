package thaumicenergistics_ce.selftest;

import appeng.api.parts.IPartHost;
import appeng.util.Platform;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aspect.TCAspects;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.ThaumicEnergistics;
import thaumicenergistics_ce.blockentity.assembler.BlockEntityArcaneAssembler;
import thaumicenergistics_ce.compat.thaumaturge.TcAura;
import thaumicenergistics_ce.part.PartVisInterface;
import thaumicenergistics_ce.part.VisReservation;

/**
 * Measures whether the Assembler beside the player can draw vis along the relay chain; read-only.
 * <ul>
 *   <li>On only with {@code THAUMICENERGISTICS_VIS_RELAY_TEST=true}.
 *   <li>A relay takes the nearest source and cannot rank an addon source, so it may end at an interface,
 *       which then answers {@code false} and sends the relay back; every hop is logged.
 * </ul>
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

        List<BlockPos> assemblers = find(level, origin,
                pos -> level.getBlockEntity(pos) instanceof BlockEntityArcaneAssembler);
        List<BlockPos> relays = find(level, origin, pos -> TcAura.isRelay(level, pos));
        List<BlockPos> interfaces = find(level, origin, pos -> partAt(level, pos) != null);

        ThaumicEnergistics.LOG.info(
                "[vistest] found {} arcane assembler(s), {} vis relay block(s), {} vis interface part(s)",
                assemblers.size(), relays.size(), interfaces.size());

        for (BlockPos relayPos : relays) {
            TcAura.RelayLink link = TcAura.link(level, relayPos);
            if (link == null) {
                continue;
            }
            TcAura.RelayEnd end = TcAura.chainEnd(level, relayPos);
            // Where the relay's chain ends: a node, another addon source, or nothing at all. A relay
            // links to the nearest source, so this says whether the interface is in the running.
            ThaumicEnergistics.LOG.info(
                    "[vistest] relay at {} linked={} depth={} parent={} resolvesTo={}",
                    relayPos, link.linked(), link.depth(), link.parent(),
                    end == null ? "NOTHING" : end.description());

            // What the node at the end of the chain can give: an energized node accrues
            // aspectsBase.amountOf(aspect) centivis a second and hands out min(allowance, stored).
            TcAura.NodeReport resolved = TcAura.nodeReport(level, relayPos);
            if (resolved != null) {
                StringBuilder palette = new StringBuilder();
                for (TcAura.NodeAspect entry : resolved.palette()) {
                    if (palette.length() > 0) {
                        palette.append(", ");
                    }
                    palette.append(entry.name())
                            .append(" amount=").append(entry.amount())
                            .append(" rate=").append(entry.rate()).append("c/s")
                            .append(" stored=").append(entry.stored());
                }
                ThaumicEnergistics.LOG.info(
                        "[vistest]   node at {} energized={} palette[{}]",
                        resolved.pos(), resolved.energized(),
                        palette.length() == 0 ? "empty" : palette);
            }
        }

        // What each interface would carry, aspect by aspect: reserve() only reports, taking neither from the
        // chain nor the network. All six aspects, as the part follows the world, not the node's palette.
        for (BlockPos interfacePos : interfaces) {
            double nearest = Double.MAX_VALUE;
            for (BlockPos assemblerPos : assemblers) {
                nearest = Math.min(nearest, Math.sqrt(interfacePos.distSqr(assemblerPos)));
            }
            // What Thaumaturge asks of a source: canSupply decides whether a relay may link at all, and the
            // per-aspect answers what it would hand over. Answering no makes it invisible to every relay.
            StringBuilder flags = new StringBuilder();
            StringBuilder carries = new StringBuilder();
            PartVisInterface part = partAt(level, interfacePos);
            BlockPos upstream = null;
            if (part == null) {
                flags.append("part-not-found");
            } else {
                flags.append("active=").append(part.isActive())
                        .append(" canSupply=").append(part.canSupply());
                upstream = part.upstreamPosition();
                for (ResourceKey<IAspect> primal : TCAspects.PRIMALS) {
                    VisReservation offer = part.reserve(primal, 100);
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

        // The measurement that answers the question: the same call the machine makes, with simulate=true so
        // nothing drains. Every primal is asked, since a node holds only what it was fed.
        for (BlockPos assemblerPos : assemblers) {
            boolean reach = TcAura.relayWithinReach(level, assemblerPos);
            StringBuilder draws = new StringBuilder();
            int total = 0;
            for (ResourceKey<IAspect> primal : TCAspects.PRIMALS) {
                // TCAspects' primals are already the ResourceKey the helper wants, not a holder.
                int drawn = TcAura.drainCentivis(level, assemblerPos, primal, 100, true);
                total += drawn;
                if (draws.length() > 0) {
                    draws.append(' ');
                }
                draws.append(primal.location().getPath()).append('=').append(drawn);
            }
            ThaumicEnergistics.LOG.info(
                    "[vistest] assembler at {}: aura={} relayBlockWithin8={} simulatedDraw[{}] total={} centivis",
                    assemblerPos,
                    TcAura.vis(level, assemblerPos),
                    reach, draws, total);
            if (!reach) {
                ThaumicEnergistics.LOG.info(
                        "[vistest]   -> no relay BLOCK within 8. A vis interface part cannot be reached from here"
                                + " on its own: place a Thaumaturge vis relay block within 8 of the machine and"
                                + " wire the interface TO THAT BLOCK (they link up to 8 apart, 16 hops).");
            } else if (total <= 0) {
                ThaumicEnergistics.LOG.info(
                        "[vistest]   -> a relay block is in range but gave nothing for any of the six aspects."
                                + " Either the relay's parent chain reaches no source at all, or the source at"
                                + " the end of it is empty.");
            } else {
                // Any non-zero answer is the chain's own; which aspects answered is the part worth reading.
                ThaumicEnergistics.LOG.info(
                        "[vistest]   -> the chain answers, in the aspects listed above and no others.");
            }
        }
    }

    /** The vis interface part mounted on the cable bus at {@code pos}, or null when there is none. */
    private static @Nullable PartVisInterface partAt(ServerLevel level, BlockPos pos) {
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

    /** Every block in the scan cube the predicate accepts. Blocks carrying a vis interface part are
     * found the same way: a part is not a block entity, so {@code partAt} asks the cable bus it is
     * mounted on, one face at a time. All three predicates are O(1): none of them scans onward. */
    private static List<BlockPos> find(ServerLevel level, BlockPos origin, Predicate<BlockPos> matches) {
        List<BlockPos> found = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = -SCAN; x <= SCAN; x++) {
            for (int y = -SCAN; y <= SCAN; y++) {
                for (int z = -SCAN; z <= SCAN; z++) {
                    cursor.setWithOffset(origin, x, y, z);
                    if (matches.test(cursor)) {
                        found.add(cursor.immutable());
                    }
                }
            }
        }
        return found;
    }
}
