package titular.modid.network;

import net.minecraft.text.Text;
import titular.modid.model.*;

import java.util.*;

/** Immutable, permission-projected state sent by the server to one client. */
public record ClientSnapshot(long revision, DisplayMode mode, PlayerTitleState self,
                             List<String> availableTitleIds, List<OnlineDisplayEntry> onlinePlayers,
                             PermissionLevel permissionLevel, boolean canManageSelfGroup,
                             boolean canManageAll, Optional<ManagementData> management,
                             Map<String, TitleDefinition> titles, Map<String, GroupDefinition> groups,
                             Map<UUID, PlayerTitleState> playerStates) {
    public ClientSnapshot {
        mode = mode == null ? DisplayMode.PREFIX : mode;
        self = self == null ? null : copyState(self);
        availableTitleIds = immutableList(availableTitleIds);
        onlinePlayers = immutableList(onlinePlayers);
        permissionLevel = permissionLevel == null ? PermissionLevel.PLAYER : permissionLevel;
        canManageSelfGroup = permissionLevel.includes(PermissionLevel.ADMIN);
        canManageAll = permissionLevel == PermissionLevel.SUPERADMIN;
        management = management == null ? Optional.empty() : management.map(m -> new ManagementData(m.groupIds(), m.titleIds(), m.playerIds(), m.settings()));
        titles = immutableMap(titles, ClientSnapshot::copyTitle);
        groups = immutableMap(groups, ClientSnapshot::copyGroup);
        playerStates = immutableMap(playerStates, ClientSnapshot::copyState);
        if (permissionLevel == PermissionLevel.PLAYER) { management = Optional.empty(); titles = Map.of(); groups = Map.of(); playerStates = Map.of(); }
        else if (permissionLevel == PermissionLevel.ADMIN) {
            management = management.map(m -> new ManagementData(m.groupIds(), List.of(), List.of(), null));
            titles = Map.of(); groups = Map.of(); playerStates = Map.of();
        }
    }

    public ClientSnapshot(long revision, DisplayMode mode, PlayerTitleState self, List<String> availableTitleIds,
                          List<OnlineDisplayEntry> onlinePlayers, PermissionLevel permissionLevel,
                          boolean canManageSelfGroup, boolean canManageAll, ManagementData management) {
        this(revision, mode, self, availableTitleIds, onlinePlayers, permissionLevel, canManageSelfGroup,
                canManageAll, Optional.ofNullable(management), null, null, null);
    }

    public ClientSnapshot(long revision, DisplayMode mode, PlayerTitleState self, List<String> availableTitleIds,
                          List<OnlineDisplayEntry> onlinePlayers, PermissionLevel permissionLevel) {
        this(revision, mode, self, availableTitleIds, onlinePlayers, permissionLevel,
                permissionLevel != null && permissionLevel.includes(PermissionLevel.ADMIN),
                permissionLevel == PermissionLevel.SUPERADMIN, Optional.empty(), null, null, null);
    }

    private static <T> List<T> immutableList(List<T> list) { return list == null ? List.of() : List.copyOf(list); }
    private interface Copier<T> { T copy(T value); }
    private static <K,V> Map<K,V> immutableMap(Map<K,V> map, Copier<V> copier) {
        if (map == null || map.isEmpty()) return Map.of();
        Map<K,V> copy = new LinkedHashMap<>(); map.forEach((k,v) -> copy.put(k, copier.copy(v))); return Collections.unmodifiableMap(copy);
    }
    static TitleDefinition copyTitle(TitleDefinition t) { return t == null ? null : new TitleDefinition(t.id(), t.prefix(), t.suffix()); }
    static GroupDefinition copyGroup(GroupDefinition g) { return g == null ? null : new GroupDefinition(g.id(), g.parent(), g.titleIds()); }
    static PlayerTitleState copyState(PlayerTitleState p) { return p == null ? null : new PlayerTitleState(p.playerId(), p.primaryGroup(), p.extraGroups(), p.extraTitles(), p.activeTitle(), p.luckPermsGroups()); }

    public record ManagementData(List<String> groupIds, List<String> titleIds, List<UUID> playerIds, TitularSettings settings) {
        public ManagementData { groupIds = immutableList(groupIds); titleIds = immutableList(titleIds); playerIds = immutableList(playerIds); }
    }
}
