package thaumicenergistics_ce.integration.ae2;

import net.minecraft.core.RegistryAccess;
import org.jspecify.annotations.Nullable;

/**
 * 客户端自己的注册表，提供给通用侧：有 level 用 level 的，在那之前用它的连接的。
 * 实现在 {@code ClientSetup} 里，这个包因此不点名任何客户端类型。
 */
public interface ClientRegistrySource {

    @Nullable RegistryAccess registries();
}
