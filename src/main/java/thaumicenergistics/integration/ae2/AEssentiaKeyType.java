package thaumicenergistics.integration.ae2;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AmountFormat;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import thaumicenergistics.ThEIds;

/**
 * The AE2 key type for Thaumaturge essentia.
 *
 * <p>Registering this is what makes essentia a first-class citizen of an ME network rather than a special
 * case bolted onto storage cells. Every place AE2 handles an {@code AEKey} - cells, buses, terminals,
 * level emitters, the crafting planner - dispatches through the key type registry, so they all start
 * working with essentia the moment this type exists.
 *
 * <p>Eight units per byte, matching the item model. Essentia is measured in the same small integers
 * Thaumaturge uses - a jar holds 250 ({@code BlockEntityJar.CAPACITY}), a phial 10
 * ({@code PhialItem.BASE_AMOUNT}), a craft pays tens - and one byte per eight is the scale the numbers
 * were designed at. Those two figures were 64 and 8 while this comment was written, taken from the
 * Thaumcraft reference build rather than measured; the ported mod says otherwise. It also keeps a 1k component the same physical size as a 1k item component:
 * 1024 bytes, holding 8192 essentia, which is the "8000" of the reference build's own documentation.
 */
public final class AEssentiaKeyType extends AEKeyType {

    /** Essentia held per byte of a storage component. */
    public static final int AMOUNT_PER_BYTE = 8;

    public static final ResourceLocation ID = ThEIds.id("essentia");

    public static final AEssentiaKeyType INSTANCE = new AEssentiaKeyType();

    private AEssentiaKeyType() {
        super(ID, AEssentiaKey.class, Component.translatable("ae2.keytype.thaumicenergistics.essentia"));
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
     * How much is moved per operation.
     *
     * <p>One, which is the base class default and the reference build's behaviour. AE2 sizes what a bus
     * moves at a time from this, and a single point per operation matches how Thaumaturge's own devices
     * hand essentia over - a tube passes one point per transfer. Buses are limited by their tick rate
     * rather than by moving a jar at a time, and a bigger number here would let one bus empty a jar
     * instantly, which is not what the reference does.
     */
    @Override
    public int getAmountPerOperation() {
        return 1;
    }

    @Override
    public int getAmountPerUnit() {
        return 1;
    }

    /** No fuzzy search. Fuzzy matching is about damage and durability, and an aspect has neither. */
    @Override
    public boolean supportsFuzzyRangeSearch() {
        return false;
    }

    /**
     * Resolves the aspect a key names, against whichever registry access is at hand.
     *
     * <p>Registries rather than a level, because that is all the lookup needs. The aspect registry is a
     * synchronised datapack registry, so a client resolves an id it was sent exactly as the server does, and
     * it has the server's registries from the moment its connection is configured - while a level only
     * exists afterwards. A key that arrives in between, which is when a terminal's contents are sent, had
     * nothing to resolve against before this took registries.
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

    /** The same, for the callers that hold a level and nothing else. */
    public static @Nullable Holder<IAspect> aspectOf(Level level, ResourceLocation id) {
        return aspectOf(level.registryAccess(), id);
    }

    /**
     * The registries an aspect can be resolved against, asked of whichever side is running.
     *
     * <p>The client's are reached by reflection rather than by naming {@code Minecraft} here. That class
     * does not exist on a dedicated server, and a common class that mentions it fails to load there - not
     * when the branch is taken, but when the class is verified. Reflection keeps the reference out of the
     * constant pool, and the branch is only ever taken with a client present.
     *
     * @return the registries, or {@code null} before either side has any
     */
    static @Nullable RegistryAccess clientOrServerRegistries() {
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            RegistryAccess client = ClientRegistriesHolder.get();
            if (client != null) {
                return client;
            }
        }
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : server.registryAccess();
    }

    /**
     * Why an aspect could not be resolved, in a few words, for the one line that reports it.
     *
     * <p>"No registries yet" and "this id is not in them" fail identically from the outside - nothing is
     * drawn - and they mean opposite things: one is too early, the other is wrong data.
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

    /** Holds the one reflective reference to the client's registries, so they are looked up once. */
    private static final class ClientRegistriesHolder {
        private static final java.lang.reflect.Method GET_INSTANCE;
        private static final java.lang.reflect.Field LEVEL;
        private static final java.lang.reflect.Method GET_CONNECTION;
        private static final java.lang.reflect.Method CONNECTION_REGISTRIES;

        static {
            java.lang.reflect.Method instance = null;
            java.lang.reflect.Field level = null;
            java.lang.reflect.Method connection = null;
            java.lang.reflect.Method connectionRegistries = null;
            try {
                Class<?> minecraft = Class.forName("net.minecraft.client.Minecraft");
                instance = minecraft.getMethod("getInstance");
                // The public field, not a getter: this Minecraft has no getLevel at all, and asking for one
                // threw into the catch below, leaving the client with no level - so every key it drew
                // resolved no aspect. A client whose only level belongs to a server had no fallback and drew
                // no aspect icons anywhere; single player was covered by the integrated server and hid it.
                level = minecraft.getField("level");
                // The connection, for the window before a level exists: its registries are already the
                // server's, and the terminal's keys are sent while the menu is opening.
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
