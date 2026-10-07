package thaumicenergistics_ce.integration.ae2;

import net.minecraft.core.RegistryAccess;
import org.jspecify.annotations.Nullable;

/**
 * 客户端在这里装入通往自身注册表的方式，好让通用代码能够索取它们
 * 而不必指名客户端类——{@code net.ClientSinks} 用的就是这个形式，理由相同。
 * 它在任何东西绘制键之前由 {@code ClientSetup} 装入，而专用
 * 服务器什么都不装。这个字段本身是通用的：两侧都会加载这个类，只有一侧
 * 会给它赋值。
 */
public final class ClientRegistries {

    private static ClientRegistrySource source;

    private ClientRegistries() {}

    public static void install(ClientRegistrySource source) {
        ClientRegistries.source = source;
    }

    /** 本侧的注册表；本侧无可提供时为 null——专用服务器什么都不装。 */
    public static @Nullable RegistryAccess get() {
        ClientRegistrySource source = ClientRegistries.source;
        return source == null ? null : source.registries();
    }
}
