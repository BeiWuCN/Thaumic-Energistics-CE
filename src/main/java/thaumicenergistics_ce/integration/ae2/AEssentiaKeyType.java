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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.compat.thaumaturge.TcRegistry;

/**
 * The AE2 key type for Thaumaturge essentia. Registering this makes essentia first-class, so
 * cells, buses, terminals and the planner all dispatch. {@code AMOUNT_PER_BYTE = 8} matches
 * Thaumaturge, where a jar holds 250 and a phial 10 (see {@code TcRegistry}); it was measured
 * in this build rather than taken from the reference's 64, and a 1k component is 1024 bytes,
 * 8192 essentia.
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

    /** The registries an aspect can be resolved against, asked of whichever side is running: the client
     * installs its own through {@link ClientRegistries}, a dedicated server is asked for the server's.
     * @return the registries, or {@code null} before either side has any
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
}
