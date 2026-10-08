package thaumicenergistics_ce.integration.ae2;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.compat.thaumaturge.TcRegistry;

/**
 * Thaumaturge 源质对应的 AE2 键类型。注册后源质是一等公民，
 * 存储元件、总线、终端和规划器都能正确分派。
 * {@code AMOUNT_PER_BYTE = 8} 与 Thaumaturge 一致：
 * 一个罐子装 250，一个小瓶装 10（见 {@code TcRegistry}）。
 * 这个值是本构建实测的，参照实现用的是 64。一个 1k 组件 1024 字节，即 8192 源质。
 */
public final class AEssentiaKeyType extends AEKeyType {

    /** 存储组件每字节装 8 源质。 */
    public static final int AMOUNT_PER_BYTE = 8;

    public static final Identifier ID = ThEIds.id("essentia");

    public static final AEssentiaKeyType INSTANCE = new AEssentiaKeyType();

    private AEssentiaKeyType() {
        super(ID, AEssentiaKey.class, Component.translatable("ae2.keytype.thaumicenergistics_ce.essentia"));
    }

    @Override
    public MapCodec<? extends AEKey> codec() {
        return AEssentiaKey.MAP_CODEC;
    }

    @Override
    public AEKey readFromPacket(RegistryFriendlyByteBuf input) {
        return AEssentiaKey.fromPacket(input);
    }

    @Override
    public int getAmountPerByte() {
        return AMOUNT_PER_BYTE;
    }

    /**
     * 每次操作搬运 1 份，基类默认值。AE2 用它决定总线一次搬多少，
     * 调大就会让一条总线瞬间抽空一个罐子。
     */
    @Override
    public int getAmountPerOperation() {
        return 1;
    }

    @Override
    public int getAmountPerUnit() {
        return 1;
    }

    /** 不做模糊搜索：模糊匹配要损伤值或耐久度，要素两样都没有。 */
    @Override
    public boolean supportsFuzzyRangeSearch() {
        return false;
    }

    /**
     * 用手边拿得到的注册表访问解析键所指的要素：用注册表不用 level，
     * 要素注册表是同步的，客户端在任何 level 存在之前就拥有它。
     *
     * @return 该要素；这些注册表里没有对应条目时为 {@code null}
     */
    public static @Nullable Holder<IAspect> aspectOf(HolderLookup.Provider registries, Identifier id) {
        var lookup = registries.lookup(IAspect.REGISTRY_KEY).orElse(null);
        return lookup == null
                ? null
                : lookup.get(ResourceKey.create(IAspect.REGISTRY_KEY, id))
                        .map(holder -> (Holder<IAspect>) holder)
                        .orElse(null);
    }

    public static @Nullable Holder<IAspect> aspectOf(Level level, Identifier id) {
        return aspectOf(level.registryAccess(), id);
    }

    /** 解析要素要用的注册表，向当前运行的那一侧索取：客户端
     * 通过 {@link ClientRegistries} 装入自己的，专用服务器则索取服务端的。
     * @return 这些注册表；在任一侧拥有它之前为 {@code null}
     */
    static @Nullable RegistryAccess clientOrServerRegistries() {
        RegistryAccess client = ClientRegistries.get();
        if (client != null) {
            return client;
        }
        var server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : server.registryAccess();
    }

    /**
     * 用几个字说明某个要素为什么解析不出来。"No registries yet" 与 "not in them"
     * 从外面看一样：都没画出来，含义却相反。
     */
    public static String whyNoAspect(Identifier id) {
        RegistryAccess registries = clientOrServerRegistries();
        if (registries == null) {
            return "no registries on either side yet";
        }
        if (registries.lookup(IAspect.REGISTRY_KEY).isEmpty()) {
            return "no " + IAspect.REGISTRY_KEY.identifier() + " registry";
        }
        return "no entry for " + id + " in " + IAspect.REGISTRY_KEY.identifier();
    }
}
