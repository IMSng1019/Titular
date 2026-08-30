package titular.modid.client.editor;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import titular.modid.network.ClientSnapshot;
import titular.modid.network.OnlineDisplayEntry;

import java.util.Objects;
import java.util.UUID;

/** Pure helpers for composing a title preview without mutating source text. */
public final class TitlePreview {
    private TitlePreview() { }

    /**
     * Builds prefix + exact username + suffix as independent siblings. The
     * username is deliberately created with an empty style so editor styles
     * cannot leak into the player's name.
     */
    public static MutableText compose(Text prefix, String username, Text suffix) {
        Objects.requireNonNull(username, "username");
        MutableText result = Text.empty();
        if (prefix != null) result.append(prefix.copy());
        result.append(Text.literal(username));
        if (suffix != null) result.append(suffix.copy());
        return result;
    }

    /**
     * Resolves the best display name available in a permission-filtered
     * snapshot. Online raw names win; callers can provide the current session
     * name for the local player and a UUID fallback for offline players.
     */
    public static String resolveUsername(ClientSnapshot snapshot, UUID target,
                                         String sessionUsername, String uuidFallback) {
        if (snapshot != null && target != null) {
            for (OnlineDisplayEntry entry : snapshot.onlinePlayers()) {
                if (entry != null && target.equals(entry.playerId())) {
                    String name = entry.rawName().getString();
                    if (!name.isBlank()) return name;
                }
            }
        }
        if (sessionUsername != null && !sessionUsername.isBlank()) return sessionUsername;
        if (uuidFallback != null && !uuidFallback.isBlank()) return uuidFallback;
        return target == null ? "username" : target.toString();
    }
}
