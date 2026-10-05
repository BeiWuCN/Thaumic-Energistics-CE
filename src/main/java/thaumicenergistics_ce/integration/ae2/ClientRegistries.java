package thaumicenergistics_ce.integration.ae2;

import net.minecraft.core.RegistryAccess;
import org.jspecify.annotations.Nullable;

/**
 * Where the client installs the way to reach its registries, so common code can ask for them without
 * naming a client class - the shape {@code net.ClientSinks} uses, for the same reason.
 * <ul>
 * <li>Installed from {@code ClientSetup} before anything draws a key; a dedicated server installs nothing.
 * <li>The field itself is common: both sides load this class, only one of them sets its value.
 * </ul>
 */
public final class ClientRegistries {

    private static ClientRegistrySource source;

    private ClientRegistries() {}

    public static void install(ClientRegistrySource source) {
        ClientRegistries.source = source;
    }

    /** This side's registries, or null when it has none to offer - a dedicated server installs nothing. */
    public static @Nullable RegistryAccess get() {
        ClientRegistrySource source = ClientRegistries.source;
        return source == null ? null : source.registries();
    }
}
