package thaumicenergistics_ce.client;

import net.minecraft.locale.Language;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.ThEConfig;

@EventBusSubscriber(modid = ThEIds.MODID, value = Dist.CLIENT)
public final class LocalizedConfigComments {
    private static ModConfig config;
    private static Object lastLoaded;
    private static Language lastLanguage;
    private static int ticks;

    private LocalizedConfigComments() {}

    @SubscribeEvent
    public static void onLoading(ModConfigEvent.Loading event) { remember(event.getConfig()); }

    @SubscribeEvent
    public static void onReloading(ModConfigEvent.Reloading event) { remember(event.getConfig()); }

    @SubscribeEvent
    public static void onUnloading(ModConfigEvent.Unloading event) {
        if (event.getConfig() == config) {
            config = null;
            lastLoaded = null;
            lastLanguage = null;
        }
    }

    private static void remember(ModConfig candidate) {
        if (candidate.getSpec() == ThEConfig.SPEC) config = candidate;
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (++ticks % 20 != 0 || config == null) return;
        var loaded = config.getLoadedConfig();
        if (loaded == null) return;
        // Remote server configurations and unloaded worlds have no writable local path.
        try {
            if (config.getFullPath() == null) return;
        } catch (IllegalStateException remoteOrUnloaded) {
            return;
        }
        var language = Language.getInstance();
        if (loaded == lastLoaded && language == lastLanguage) return;
        lastLoaded = loaded;
        lastLanguage = language;
        // Values already validated by FML. Update only the documented comments.
        var data = loaded.config();
        var spec = ThEConfig.SPEC.getSpec();
        boolean changed = updateComments(data, spec);
        if (changed) loaded.save();
    }

    private static boolean updateComments(com.electronwill.nightconfig.core.CommentedConfig data,
            com.electronwill.nightconfig.core.UnmodifiableConfig spec) {
        boolean changed = false;
        for (var entry : spec.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof net.neoforged.neoforge.common.ModConfigSpec.ValueSpec definition) {
                var comment = definition.getComment();
                if (!java.util.Objects.equals(data.getComment(entry.getKey()), comment)) {
                    data.setComment(entry.getKey(), comment);
                    changed = true;
                }
            } else if (value instanceof com.electronwill.nightconfig.core.UnmodifiableConfig child
                    && data.get(entry.getKey()) instanceof com.electronwill.nightconfig.core.CommentedConfig section) {
                changed |= updateComments(section, child);
            }
        }
        return changed;
    }
}
