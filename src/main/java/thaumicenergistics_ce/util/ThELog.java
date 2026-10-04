package thaumicenergistics_ce.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds the mod logger so that the classes which write log lines do not have to import the
 * {@code @Mod} composition root.
 *
 * <p>A plain holder rather than a wrapper: the {@link Logger} instance and the name passed to
 * {@link LoggerFactory#getLogger(String)} are exactly what the root class used, so log output is
 * unchanged.
 */
public final class ThELog {
    private ThELog() {}

    public static final Logger LOG = LoggerFactory.getLogger("ThaumicEnergistics");
}