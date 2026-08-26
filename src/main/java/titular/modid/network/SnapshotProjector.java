package titular.modid.network;

import net.minecraft.text.Text;
import titular.modid.model.*;
import titular.modid.service.TitlePoolResolver;

import java.util.*;

/** Builds the least-privilege snapshot for a particular recipient. */
public final class SnapshotProjector {
    private SnapshotProjector() {}
    public record OnlinePlayer(UUID playerId, Text rawName) {}

    public static ClientSnapshot project(TitularData data, UUID recipient, PermissionLevel level,
                                         List<OnlinePlayer> online) {
        Objects.requireNonNull(data, "data");
        level = level == null ? PermissionLevel.PLAYER : level;
        PlayerTitleState self = recipient == null ? null : data.players().get(recipient);
        if (self == null && recipient != null) self = new PlayerTitleState(recipient);
        List<String> available = self == null ? List.of() : new TitlePoolResolver().resolve(self, data).stream()
                .limit(TitularLimits.MAX_REFERENCE_LIST).toList();
        List<OnlineDisplayEntry> entries = new ArrayList<>();
        if (online != null) for (OnlinePlayer player : online) {
            if (entries.size() >= TitularLimits.MAX_REFERENCE_LIST) break;
            if (player == null || player.playerId() == null || player.rawName() == null) continue;
            PlayerTitleState state = data.players().get(player.playerId());
            TitleDefinition active = null;
            if (state != null && state.activeTitle() != null) {
                List<String> pool = new TitlePoolResolver().resolve(state, data);
                if (pool.contains(state.activeTitle())) active = data.titles().get(state.activeTitle());
            }
            entries.add(new OnlineDisplayEntry(player.playerId(), player.rawName(), active));
        }
        ClientSnapshot.ManagementData management = null;
        Map<String,TitleDefinition> titles = Map.of(); Map<String,GroupDefinition> groups = Map.of(); Map<UUID,PlayerTitleState> players = Map.of();
        if (level.includes(PermissionLevel.ADMIN)) {
            management = new ClientSnapshot.ManagementData(data.groups().keySet().stream().limit(TitularLimits.MAX_REFERENCE_LIST).toList(),
                    level == PermissionLevel.SUPERADMIN ? data.titles().keySet().stream().limit(TitularLimits.MAX_REFERENCE_LIST).toList() : List.of(),
                    level == PermissionLevel.SUPERADMIN ? data.players().keySet().stream().limit(TitularLimits.MAX_REFERENCE_LIST).toList() : List.of(),
                    level == PermissionLevel.SUPERADMIN ? data.settings() : null);
        }
        if (level == PermissionLevel.SUPERADMIN) {
            titles = limitedTitles(data.titles());
            groups = limitedGroups(data.groups());
            players = limitedPlayers(data.players());
        }
        return new ClientSnapshot(data.revision(), data.settings().displayMode(), self, available, entries, level,
                level.includes(PermissionLevel.ADMIN), level == PermissionLevel.SUPERADMIN, Optional.ofNullable(management), titles, groups, players);
    }

    private static Map<String, TitleDefinition> limitedTitles(Map<String, TitleDefinition> source) {
        Map<String, TitleDefinition> result = new LinkedHashMap<>();
        source.entrySet().stream().limit(TitularLimits.MAX_DEFINITIONS).forEach(entry -> result.put(entry.getKey(), entry.getValue()));
        return result;
    }

    private static Map<String, GroupDefinition> limitedGroups(Map<String, GroupDefinition> source) {
        Map<String, GroupDefinition> result = new LinkedHashMap<>();
        source.entrySet().stream().limit(TitularLimits.MAX_DEFINITIONS).forEach(entry -> result.put(entry.getKey(), entry.getValue()));
        return result;
    }

    private static Map<UUID, PlayerTitleState> limitedPlayers(Map<UUID, PlayerTitleState> source) {
        Map<UUID, PlayerTitleState> result = new LinkedHashMap<>();
        source.entrySet().stream().limit(TitularLimits.MAX_DEFINITIONS).forEach(entry -> result.put(entry.getKey(), entry.getValue()));
        return result;
    }
}
