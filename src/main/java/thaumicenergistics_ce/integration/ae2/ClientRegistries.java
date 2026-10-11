package thaumicenergistics_ce.integration.ae2;

import net.minecraft.core.RegistryAccess;
import org.jspecify.annotations.Nullable;

public final class ClientRegistries {

    private static ClientRegistrySource source;

    private ClientRegistries() {}

    public static void install(ClientRegistrySource source) {
        ClientRegistries.source = source;
    }

    public static @Nullable RegistryAccess get() {
        ClientRegistrySource source = ClientRegistries.source;
        return source == null ? null : source.registries();
    }
}
