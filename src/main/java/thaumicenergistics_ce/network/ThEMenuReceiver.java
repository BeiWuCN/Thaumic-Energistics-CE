package thaumicenergistics_ce.network;

/**
 * The one thing every serverbound payload needs from its receiver: which menu it was addressed to, so a
 * packet that arrives after the screen closed is dropped instead of landing on whatever opened next.
 */
public interface ThEMenuReceiver {

    int containerId();
}
