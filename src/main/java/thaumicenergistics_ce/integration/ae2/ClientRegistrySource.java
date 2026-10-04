package thaumicenergistics_ce.integration.ae2;

import net.minecraft.core.RegistryAccess;
import org.jspecify.annotations.Nullable;

/**
 * The client's own registries, offered to the common side: the level's when a level exists, its
 * connection's before that. Implemented in {@code ClientSetup}, so this package names no client type.
 */
public interface ClientRegistrySource {

    @Nullable RegistryAccess registries();
}
