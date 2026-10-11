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

public final class AEssentiaKeyType extends AEKeyType {

    public static final int AMOUNT_PER_BYTE = 8;

    public static final Identifier ID = ThEIds.id("essentia");

    public static final AEssentiaKeyType INSTANCE = new AEssentiaKeyType();

    private AEssentiaKeyType() {
        super(ID, AEssentiaKey.class, Component.translatable("ae2.keytype.thaumicenergistics_ce.integration.ae2.essentia"));
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

    @Override
    public int getAmountPerOperation() {
        return 1;
    }

    @Override
    public int getAmountPerUnit() {
        return 1;
    }

    @Override
    public boolean supportsFuzzyRangeSearch() {
        return false;
    }

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

    static @Nullable RegistryAccess clientOrServerRegistries() {
        RegistryAccess client = ClientRegistries.get();
        if (client != null) {
            return client;
        }
        var server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : server.registryAccess();
    }

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
