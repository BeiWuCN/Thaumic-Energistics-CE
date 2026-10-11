package thaumicenergistics_ce.recipe;

import appeng.client.gui.me.search.RepoSearch;
import appeng.menu.me.common.GridInventoryEntry;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.FormattedCharSequence;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;

public final class EssentiaSearchChecks {
    private EssentiaSearchChecks() {}

    public static void run(MinecraftServer server) {
        Language previous = Language.getInstance();
        try {
            for (String language : new String[] {"en_us", "zh_cn", "zh_tw", "fr_fr"}) {
                Map<String, String> names = read("en_us");
                names.putAll(read(language));
                Language.inject(new TestLanguage(names));
                int count = 0;
                for (var aspect : server.registryAccess().lookupOrThrow(IAspect.REGISTRY_KEY).listElements().toList()) {
                    Identifier id = aspect.key().identifier();
                    if (!id.getNamespace().equals("thaumaturge")) {
                        continue;
                    }
                    String expected = names.get("thaumicenergistics_ce.essentia_name.thaumaturge." + id.getPath());
                    require(expected != null, "Missing English fallback for " + id);
                    AEssentiaKey key = AEssentiaKey.of(id);
                    require(key.getDisplayName().getString().equals(expected), "Stale language on cached key: " + id);
                    String localized = expected;
                    require(!expected.contains(" ("), "Display name still contains a Latin suffix: " + expected);
                    var entry = new GridInventoryEntry(++count, key, 10, 0, false);
                    require(matches(localized, entry), "Localized name cannot be searched: " + expected);
                    if (language.equals("en_us") || language.equals("fr_fr")) {
                        require(matches(id.getPath(), entry), "Latin name cannot be searched: " + id);
                    }
                }
                require(count == 37, "Aspect coverage changed: " + count);
                var fire = new GridInventoryEntry(100, AEssentiaKey.of(Identifier.parse("thaumaturge:ignis")), 10, 0, false);
                require(!matches("water", fire), "Unrelated name matched");
                if (language.equals("fr_fr")) {
                    require(matches("IGNIS", fire), "Unsupported language did not fall back to English Latin names");
                    require(!matches("火", fire), "Previous Chinese name leaked into English fallback");
                }
            }
            System.out.println("[essentia-search] PASS: 37 aspects, simplified/traditional/English, English fallback, Latin names and cached-key language changes");
        } finally {
            Language.inject(previous);
        }
    }

    private static boolean matches(String query, GridInventoryEntry entry) {
        RepoSearch search = new RepoSearch();
        search.setSearchString(query);
        return search.matches(entry);
    }

    private static Map<String, String> read(String language) {
        Map<String, String> data = new HashMap<>();
        try (InputStream stream = EssentiaSearchChecks.class.getResourceAsStream(
                "/assets/thaumicenergistics_ce/lang/" + language + ".json")) {
            if (stream != null) {
                Language.loadFromJson(stream, data::put);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load test language " + language, e);
        }
        return data;
    }

    private static final class TestLanguage extends Language {
        private final Map<String, String> names;

        private TestLanguage(Map<String, String> names) {
            this.names = names;
        }

        @Override
        public String getOrDefault(String key, String fallback) {
            return names.getOrDefault(key, fallback);
        }

        @Override
        public boolean has(String key) {
            return names.containsKey(key);
        }

        @Override
        public boolean isDefaultRightToLeft() {
            return false;
        }

        @Override
        public FormattedCharSequence getVisualOrder(FormattedText text) {
            return FormattedCharSequence.EMPTY;
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
