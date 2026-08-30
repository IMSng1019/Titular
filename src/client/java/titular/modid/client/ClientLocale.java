package titular.modid.client;

import java.util.Locale;

/** Locales supported by the client-only UI. IDs match bundled resource names. */
public enum ClientLocale {
    EN_US("en_us", "English"),
    ZH_CN("zh_cn", "中文");

    /** Readable aliases for callers that describe locales by language. */
    public static final ClientLocale ENGLISH = EN_US;
    public static final ClientLocale CHINESE = ZH_CN;

    private final String id;
    private final String displayName;

    ClientLocale(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() { return id; }

    public String displayName() { return displayName; }

    public static ClientLocale fromId(String id) {
        if (id == null) return EN_US;
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        for (ClientLocale locale : values()) {
            if (locale.id.equals(normalized)) return locale;
        }
        return EN_US;
    }
}
