package thaumicenergistics_ce.part;

public interface VisReservation {

    int amount();

    int commit();

    void close();
}
