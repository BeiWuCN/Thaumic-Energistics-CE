package thaumicenergistics_ce.init;

/**
 * The seam between the mod and its self-tests. The tests live in their own source set
 * ({@code src/selftest/java}), so the release jar carries none of them and can never fail on one.
 * A build that has them publishes an implementation through {@code ServiceLoader}, whose provider
 * list comes from that source set's resources rather than from this one.
 */
public interface SelfTestProvider {
    /**
     * Wires this build's self-tests to the game bus. Called once, from the mod constructor; each test
     * still self-guards on its own {@code THAUMICENERGISTICS_*} switch.
     */
    void registerSelfTests();
}
