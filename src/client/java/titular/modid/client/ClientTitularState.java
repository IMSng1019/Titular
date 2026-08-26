package titular.modid.client;

import titular.modid.format.DisplayAdapters;
import titular.modid.network.ClientSnapshot;
import titular.modid.network.OnlineDisplayEntry;

import java.util.Optional;
import java.util.UUID;

/** Client-side mirror of the latest server-authoritative Titular projection. */
public final class ClientTitularState {
    private ClientTitularState() { }

    /** Atomically replaces the complete projection; partial updates are not exposed. */
    public static void replace(ClientSnapshot snapshot) {
        DisplayAdapters.replaceSnapshot(snapshot);
    }

    public static ClientSnapshot current() {
        return DisplayAdapters.currentSnapshot();
    }

    public static long expectedRevision() {
        ClientSnapshot snapshot = current();
        return snapshot == null ? -1L : snapshot.revision();
    }

    public static Optional<OnlineDisplayEntry> find(UUID playerId) {
        if (playerId == null) return Optional.empty();
        ClientSnapshot snapshot = current();
        if (snapshot == null) return Optional.empty();
        return snapshot.onlinePlayers().stream()
                .filter(entry -> playerId.equals(entry.playerId()))
                .findFirst();
    }

    public static void clear() {
        DisplayAdapters.clearSnapshot();
    }
}
