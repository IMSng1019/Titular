package titular.modid.format;

import net.minecraft.text.Text;
import titular.modid.model.TitleDefinition;
import titular.modid.network.ClientSnapshot;
import titular.modid.network.OnlineDisplayEntry;

import java.util.Objects;
import java.util.UUID;

/**
 * Thin display adapters shared by client render hooks and common integrations.
 *
 * <p>The client installs the current server projection with
 * {@link #replaceSnapshot(ClientSnapshot)}. Until then, and whenever an entry
 * has no active title, these methods return a copy of the original name.</p>
 */
public final class DisplayAdapters {
    private static volatile ClientSnapshot snapshot;

    private DisplayAdapters() { }

    public static void replaceSnapshot(ClientSnapshot next) {
        snapshot = next;
    }

    public static void clearSnapshot() {
        snapshot = null;
    }

    public static ClientSnapshot currentSnapshot() {
        return snapshot;
    }

    public static Text formatHeadName(Text rawName, UUID playerId) {
        return format(rawName, playerId);
    }

    public static Text formatTabName(Text rawName, UUID playerId) {
        return format(rawName, playerId);
    }

    public static Text formatChatName(Text rawName, UUID playerId) {
        return format(rawName, playerId);
    }

    /** Formats a name using an explicit projection, useful to server adapters. */
    public static Text format(Text rawName, OnlineDisplayEntry entry, ClientSnapshot projection) {
        Objects.requireNonNull(rawName, "rawName");
        if (entry == null || entry.activeTitle() == null || projection == null) {
            return rawName.copy();
        }
        return TitularFormatter.format(rawName, entry.activeTitle(), projection.mode());
    }

    private static Text format(Text rawName, UUID playerId) {
        Objects.requireNonNull(rawName, "rawName");
        ClientSnapshot current = snapshot;
        if (current == null || playerId == null) return rawName.copy();
        OnlineDisplayEntry entry = current.onlinePlayers().stream()
                .filter(candidate -> playerId.equals(candidate.playerId()))
                .findFirst().orElse(null);
        return format(rawName, entry, current);
    }
}
