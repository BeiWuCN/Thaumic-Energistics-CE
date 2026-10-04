package thaumicenergistics_ce.part;

/**
 * A claim on a Vis Interface's vis and on the ME network's energy behind it. TECE's own type: Thaumaturge
 * 0.4.6 replaced its reservation model with a pull model, {@code IVisRelaySource} now asking for a number
 * of centivis and taking them in one call, so the {@code Reservation} the two sides used to share is gone.
 * The Arcane Assembler still wants to ask before it pays - it compares sources and picks one - so the
 * two-step shape is kept here, on the TECE side of the boundary.
 */
public interface VisReservation {

    int amount();

    int commit();

    void close();
}
