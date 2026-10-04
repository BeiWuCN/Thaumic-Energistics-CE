package thaumicenergistics_ce.integration.ae2;

import net.minecraft.core.RegistryAccess;
import org.jspecify.annotations.Nullable;

/**
 * Where the client installs the way to reach its registries, so the key type can ask for them without
 * naming a client class - the shape {@code net.ClientSinks} uses, for the same reason.
 * <ul>
 * <li>Installed from {@code ClientSetup} on the client setup event, before anything draws a key; on a
 * dedicated server nothing is installed and the asker falls through to the server's registries.
 * <li>The field itself is common: both sides load this class, only one of them sets its value.
 * </ul>
 */
public final class ClientRegistries {

    private static ClientRegistrySource source;

    private ClientRegistries() {}

    public static void install(ClientRegistrySource source) {
        ClientRegistries.source = source;
    }

    static @Nullable RegistryAccess get() {
        ClientRegistrySource source = ClientRegistries.source;
        return source == null ? null : source.registries();
    }
}
