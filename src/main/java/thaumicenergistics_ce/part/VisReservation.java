package thaumicenergistics_ce.part;

/**
 * A claim on a Vis Interface's vis and on the ME network's energy behind it.
 *
 * <p>This is TECE's own type. Thaumaturge 0.4.6 replaced its reservation model with a pull model:
 * {@code IVisRelaySource} now asks for a number of centivis and takes them in one call, so the
 * {@code Reservation} the two sides used to share is gone. The Arcane Assembler still wants to ask
 * before it pays - it compares several sources and picks one - so the two-step shape is kept here,
 * on the TECE side of the boundary, and no longer crosses into Thaumaturge.
 */
public interface VisReservation {

    /** How many centivis were offered. Fixed when the reservation was made, not when it is taken. */
    int amount();

    /** Hands the vis over and takes the AE; {@code 0} when the offer no longer stands. */
    int commit();

    /** Releases the claim. Nothing moves before {@link #commit()}, so this is bookkeeping only. */
    void close();
}
