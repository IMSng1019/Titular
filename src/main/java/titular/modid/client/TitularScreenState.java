package titular.modid.client;

import titular.modid.model.PermissionLevel;
import titular.modid.network.ClientSnapshot;
import titular.modid.network.TitularRequest;

import java.util.ArrayList;
import java.util.List;

/** Pure projection and request helpers shared by the client screen and tests. */
public record TitularScreenState(long revision, String selectedTitle, Tab tab) {
    public enum Tab { TITLES, SELF_GROUP, PLAYERS, GROUPS, TITLE_EDITOR, SETTINGS }

    public static List<Tab> tabs(PermissionLevel level) {
        List<Tab> result = new ArrayList<>();
        result.add(Tab.TITLES);
        if (level != null && level.includes(PermissionLevel.ADMIN)) result.add(Tab.SELF_GROUP);
        if (level == PermissionLevel.SUPERADMIN) {
            result.add(Tab.PLAYERS); result.add(Tab.GROUPS); result.add(Tab.TITLE_EDITOR); result.add(Tab.SETTINGS);
        }
        return List.copyOf(result);
    }

    public static TitularScreenState from(ClientSnapshot snapshot, TitularScreenState previous) {
        if (snapshot == null) return new TitularScreenState(-1L, null, Tab.TITLES);
        String selected = previous == null ? null : previous.selectedTitle();
        if (selected == null || !snapshot.availableTitleIds().contains(selected)) {
            selected = snapshot.self() == null ? null : snapshot.self().activeTitle();
            if (selected != null && !snapshot.availableTitleIds().contains(selected)) selected = null;
        }
        Tab tab = previous == null ? Tab.TITLES : previous.tab();
        if (!tabs(snapshot.permissionLevel()).contains(tab)) tab = Tab.TITLES;
        return new TitularScreenState(snapshot.revision(), selected, tab);
    }

    public static TitularRequest activateRequest(ClientSnapshot snapshot, String titleId) {
        return TitularRequest.activate(titleId, revision(snapshot));
    }
    public static TitularRequest clearRequest(ClientSnapshot snapshot) { return TitularRequest.clear(revision(snapshot)); }
    public static TitularRequest primaryGroupRequest(ClientSnapshot snapshot, String groupId) {
        return TitularRequest.setPrimaryGroup(groupId, revision(snapshot));
    }
    private static long revision(ClientSnapshot snapshot) { return snapshot == null ? -1L : snapshot.revision(); }
}
