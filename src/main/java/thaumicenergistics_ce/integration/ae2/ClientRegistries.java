package thaumicenergistics_ce.integration.ae2;

import net.minecraft.core.RegistryAccess;
import org.jspecify.annotations.Nullable;

/**
 * 客户端在这里装上通往自身注册表的方式，通用代码就能不点名客户端类地索取它们，
 * {@code net.ClientSinks} 同一形式，理由相同。它在东西画 key 之前由 {@code ClientSetup} 装上，
 * 专用服务端什么都不装。字段本身是通用的：两侧都加载这个类，只有一侧给它赋值。
 */
public final class ClientRegistries {

    private static ClientRegistrySource source;

    private ClientRegistries() {}

    public static void install(ClientRegistrySource source) {
        ClientRegistries.source = source;
    }

    /** 本侧的注册表，本侧没有则为 null。专用服务端什么都不装。 */
    public static @Nullable RegistryAccess get() {
        ClientRegistrySource source = ClientRegistries.source;
        return source == null ? null : source.registries();
    }
}
