package titular.modid.client;

import net.minecraft.text.Text;

/** Convenience factory for client-local translated text. */
public final class ClientText {
    private ClientText() { }

    public static Text text(String key, Object... args) {
        return Text.literal(ClientLanguageManager.global().text(key, args));
    }

    public static Text text(ClientLanguageManager manager, String key, Object... args) {
        ClientLanguageManager source = manager == null ? ClientLanguageManager.global() : manager;
        return Text.literal(source.text(key, args));
    }
}
