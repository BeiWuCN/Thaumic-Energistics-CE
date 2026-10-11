package thaumicenergistics_ce.integration.ae2;

import net.minecraft.core.RegistryAccess;
import org.jspecify.annotations.Nullable;

public interface ClientRegistrySource {

    @Nullable RegistryAccess registries();
}
