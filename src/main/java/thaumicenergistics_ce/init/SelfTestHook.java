package thaumicenergistics_ce.init;

import java.util.ServiceLoader;
import thaumicenergistics_ce.util.ThELog;

/**
 * Calls whatever self-tests this classpath happens to carry. The release jar carries none - the
 * providers come from {@code src/selftest/java}, a source set main does not compile against and the
 * jar does not package - so this is a no-op there. Silence is the wrong answer when a switch is set,
 * though: a run whose {@code THAUMICENERGISTICS_*} variable reached no listener reports nothing at
 * all, which reads exactly like a pass.
 */
public final class SelfTestHook {
    private SelfTestHook() {}

    /**
     * Hands each provider on this classpath the game bus, once, from the mod constructor.
     * One that throws is reported rather than swallowed: it would take the tests behind it too.
     */
    public static void install() {
        int providers = 0;
        try {
            for (SelfTestProvider provider : ServiceLoader.load(SelfTestProvider.class)) {
                provider.registerSelfTests();
                providers++;
            }
        } catch (Throwable failure) {
            ThELog.LOG.error("a self-test provider failed to load; the tests behind it are not wired",
                    failure);
            return;
        }
        if (providers == 0 && aSwitchIsSet()) {
            ThELog.LOG.warn("a THAUMICENERGISTICS_* switch is set but this build carries no self-tests."
                    + " Use the dev run (runClient/runServer) - a release jar has none of them.");
        }
    }

    /** True when a {@code THAUMICENERGISTICS_*} variable reached this JVM - the prefix the build sets. */
    private static boolean aSwitchIsSet() {
        for (String name : System.getenv().keySet()) {
            if (name.startsWith("THAUMICENERGISTICS_")) {
                return true;
            }
        }
        return false;
    }
}
