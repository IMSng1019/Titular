package titular.modid.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Loads client-local UI strings independently of Minecraft's global language option. */
public final class ClientLanguageManager {
    private static final String MOD_ID = "titular";
    private static volatile ClientLanguageManager global;

    private final Path configPath;
    private final Map<String, String> english;
    private final Map<ClientLocale, Map<String, String>> translations;
    private ClientLocale locale;

    public ClientLanguageManager() {
        this(defaultConfigPath());
    }

    public ClientLanguageManager(Path configPath) {
        this.configPath = configPath == null ? defaultConfigPath() : configPath;
        this.english = loadResource(ClientLocale.EN_US);
        Map<ClientLocale, Map<String, String>> loaded = new LinkedHashMap<>();
        loaded.put(ClientLocale.EN_US, english);
        for (ClientLocale candidate : ClientLocale.values()) {
            if (candidate != ClientLocale.EN_US) loaded.put(candidate, loadResource(candidate));
        }
        this.translations = Collections.unmodifiableMap(loaded);
        this.locale = readLocale(this.configPath);
    }

    public static ClientLanguageManager global() {
        ClientLanguageManager result = global;
        if (result == null) {
            synchronized (ClientLanguageManager.class) {
                result = global;
                if (result == null) {
                    result = new ClientLanguageManager(defaultConfigPath());
                    global = result;
                }
            }
        }
        return result;
    }

    public static ClientLocale getLocale() { return global().locale(); }

    public static void setGlobalLocale(ClientLocale locale) { global().setLocale(locale); }

    public ClientLocale locale() { return locale; }

    /** Selects and immediately persists a locale; null is treated as English. */
    public void setLocale(ClientLocale locale) {
        this.locale = locale == null ? ClientLocale.EN_US : locale;
        persist();
    }

    public void setLocale(String localeId) {
        setLocale(ClientLocale.fromId(localeId));
    }

    public String text(String key, Object... args) {
        if (key == null || key.isBlank()) return "";
        String template = translations.getOrDefault(locale, english).get(key);
        if (template == null) template = english.getOrDefault(key, key);
        if (args == null || args.length == 0) return template;
        try {
            return String.format(java.util.Locale.ROOT, template, args);
        } catch (RuntimeException ignored) {
            return template;
        }
    }

    private void persist() {
        try {
            Path parent = configPath.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(configPath, "{\n  \"locale\": \"" + locale.id() + "\"\n}\n", StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException ignored) {
            // A read-only config must not make the client UI unusable.
        }
    }

    private static ClientLocale readLocale(Path path) {
        if (path == null || !Files.isRegularFile(path)) return ClientLocale.EN_US;
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) return ClientLocale.EN_US;
            JsonElement value = parsed.getAsJsonObject().get("locale");
            return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                    ? ClientLocale.fromId(value.getAsString()) : ClientLocale.EN_US;
        } catch (RuntimeException | IOException ignored) {
            return ClientLocale.EN_US;
        }
    }

    private static Path defaultConfigPath() {
        try {
            return FabricLoader.getInstance().getConfigDir().resolve("titular-client.json");
        } catch (Throwable ignored) {
            return Path.of("config", "titular-client.json");
        }
    }

    private static Map<String, String> loadResource(ClientLocale locale) {
        String resource = "/assets/" + MOD_ID + "/lang/" + locale.id() + ".json";
        try (InputStream stream = ClientLanguageManager.class.getResourceAsStream(resource)) {
            if (stream == null) return Map.of();
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonElement parsed = JsonParser.parseReader(reader);
                if (!parsed.isJsonObject()) return Map.of();
                Map<String, String> result = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> entry : parsed.getAsJsonObject().entrySet()) {
                    JsonElement value = entry.getValue();
                    if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                        result.put(entry.getKey(), value.getAsString());
                    }
                }
                return Collections.unmodifiableMap(result);
            }
        } catch (IOException | RuntimeException ignored) {
            return Map.of();
        }
    }
}
