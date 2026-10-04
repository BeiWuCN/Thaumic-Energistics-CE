package thaumicenergistics_ce.integration.ae2;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.mojang.serialization.MapCodec;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.compat.thaumaturge.TcRegistry;

/**
 * The AE2 key type for Thaumaturge essentia.
 * <ul>
 * <li>Registering this makes essentia first-class: cells, buses, terminals and the planner all dispatch.
 * <li>{@code AMOUNT_PER_BYTE = 8} matches Thaumaturge: a jar holds 250, a phial 10 (see {@code TcRegistry}).
 * <li>Measured in this build, not the reference's 64: a 1k component is 1024 bytes, 8192 essentia.
 * </ul>
 */
public final class AEssentiaKeyType extends AEKeyType {

    /** Essentia held per byte of a storage component. */
    public static final int AMOUNT_PER_BYTE = 8;

    public static final ResourceLocation ID = ThEIds.id("essentia");

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
     * How much is moved per operation: one, the base class default. AE2 sizes what a bus moves from
     * this, and a bigger number would let one bus empty a jar instantly.
     */
    @Override
    public int getAmountPerOperation() {
        return 1;
    }

    @Override
    public int getAmountPerUnit() {
        return 1;
    }

    /** No fuzzy search: fuzzy matching needs damage or durability, and an aspect has neither. */
    @Override
    public boolean supportsFuzzyRangeSearch() {
        return false;
    }

    /**
     * Resolves the aspect a key names against whichever registry access is at hand: registries, not a
     * level, because the aspect registry is synchronised and the client has it before any level exists.
     *
     * @return the aspect, or {@code null} when those registries have no such entry
     */
    public static @Nullable Holder<IAspect> aspectOf(HolderLookup.Provider registries, ResourceLocation id) {
        var lookup = registries.lookup(IAspect.REGISTRY_KEY).orElse(null);
        return lookup == null
                ? null
                : lookup.get(ResourceKey.create(IAspect.REGISTRY_KEY, id))
                        .map(holder -> (Holder<IAspect>) holder)
                        .orElse(null);
    }

    public static @Nullable Holder<IAspect> aspectOf(Level level, ResourceLocation id) {
        return aspectOf(level.registryAccess(), id);
    }

    /**
     * The registries an aspect can be resolved against, asked of whichever side is running: the client's
     * are reached by reflection, since naming {@code Minecraft} refuses to load on a dedicated server.
     *
     * @return the registries, or {@code null} before either side has any
     */
    static @Nullable RegistryAccess clientOrServerRegistries() {
        if (FMLEnvironment.dist.isClient()) {
            RegistryAccess client = ClientRegistriesHolder.get();
            if (client != null) {
                return client;
            }
        }
        var server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : server.registryAccess();
    }

    /**
     * Why an aspect could not be resolved, in a few words. "No registries yet" and "not in them"
     * look alike from outside - nothing is drawn - but mean opposite things.
     */
    public static String whyNoAspect(ResourceLocation id) {
        RegistryAccess registries = clientOrServerRegistries();
        if (registries == null) {
            return "no registries on either side yet";
        }
        if (registries.lookup(IAspect.REGISTRY_KEY).isEmpty()) {
            return "no " + IAspect.REGISTRY_KEY.location() + " registry";
        }
        return "no entry for " + id + " in " + IAspect.REGISTRY_KEY.location();
    }

    /** The one reflective reference to the client's registries; looked up once. */
    private static final class ClientRegistriesHolder {
        private static final Method GET_INSTANCE;
        private static final Field LEVEL;
        private static final Method GET_CONNECTION;
        private static final Method CONNECTION_REGISTRIES;

        static {
            Method instance = null;
            Field level = null;
            Method connection = null;
            Method connectionRegistries = null;
            try {
                Class<?> minecraft = Class.forName("net.minecraft.client.Minecraft");
                instance = minecraft.getMethod("getInstance");
                // Field, not a getter: this Minecraft has no getLevel, and asking for one throws.
                level = minecraft.getField("level");
                // Connection: before a level exists, its registries are already the server's.
                connection = minecraft.getMethod("getConnection");
                connectionRegistries = Class.forName("net.minecraft.client.multiplayer.ClientPacketListener")
                        .getMethod("registryAccess");
            } catch (ReflectiveOperationException ignored) {
                // Not a client, or the class is shaped differently: the server path covers it.
            }
            GET_INSTANCE = instance;
            LEVEL = level;
            GET_CONNECTION = connection;
            CONNECTION_REGISTRIES = connectionRegistries;
        }

        static @Nullable RegistryAccess get() {
            if (GET_INSTANCE == null || LEVEL == null) {
                return null;
            }
            try {
                Object minecraft = GET_INSTANCE.invoke(null);
                if (minecraft == null) {
                    return null;
                }
                Object level = LEVEL.get(minecraft);
                if (level instanceof Level clientLevel) {
                    return clientLevel.registryAccess();
                }
                if (GET_CONNECTION == null || CONNECTION_REGISTRIES == null) {
                    return null;
                }
                Object connection = GET_CONNECTION.invoke(minecraft);
                return connection == null ? null : (RegistryAccess) CONNECTION_REGISTRIES.invoke(connection);
            } catch (ReflectiveOperationException | ClassCastException ignored) {
                return null;
            }
        }
    }
}
