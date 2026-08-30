package titular.modid.client;

import titular.modid.model.PermissionLevel;
import titular.modid.network.ClientSnapshot;
import titular.modid.network.TitularRequest;
import titular.modid.client.screen.LandingAction;

import java.util.ArrayList;
import java.util.List;

/** Pure projection and request helpers shared by the client screen and tests. */
public record TitularScreenState(long revision, String selectedTitle, Tab tab, Page page) {
    public enum Tab { TITLES, SELF_GROUP, PLAYERS, GROUPS, TITLE_EDITOR, SETTINGS }

    /** Top-level routes exposed by the guided screen. */
    public enum Page { HOME, TITLE_SWITCH, PRIMARY_GROUP, MANAGEMENT, LANGUAGE }

    public TitularScreenState {
        tab = tab == null ? Tab.TITLES : tab;
        page = page == null ? Page.HOME : page;
    }

    /** Compatibility constructor retained for callers of the original state model. */
    public TitularScreenState(long revision, String selectedTitle, Tab tab) {
        this(revision, selectedTitle, tab, Page.HOME);
    }

    /** Convenience constructor for route-focused callers that do not need a tab. */
    public TitularScreenState(long revision, String selectedTitle, Page page) {
        this(revision, selectedTitle, Tab.TITLES, page);
    }

    /** Convenience overload for callers that naturally specify the route first. */
    public TitularScreenState(long revision, String selectedTitle, Page page, Tab tab) {
        this(revision, selectedTitle, tab, page);
    }

    public static List<Tab> tabs(PermissionLevel level) {
        List<Tab> result = new ArrayList<>();
        result.add(Tab.TITLES);
        if (level != null && level.includes(PermissionLevel.ADMIN)) result.add(Tab.SELF_GROUP);
        if (level == PermissionLevel.SUPERADMIN) {
            result.add(Tab.PLAYERS); result.add(Tab.GROUPS); result.add(Tab.TITLE_EDITOR); result.add(Tab.SETTINGS);
        }
        return List.copyOf(result);
    }

    /** Returns the stable, permission-filtered actions for the landing page. */
    public static List<LandingAction> actions(PermissionLevel level) {
        return LandingAction.visible(level);
    }

    /** Alias retained for UI callers that describe this as a landing projection. */
    public static List<LandingAction> landingActions(PermissionLevel level) {
        return actions(level);
    }

    public static boolean pageAllowed(Page page, PermissionLevel level) {
        if (page == null || page == Page.HOME || page == Page.LANGUAGE || page == Page.TITLE_SWITCH) return true;
        PermissionLevel effective = level == null ? PermissionLevel.PLAYER : level;
        return page == Page.PRIMARY_GROUP
                ? effective.includes(PermissionLevel.ADMIN)
                : page == Page.MANAGEMENT && effective == PermissionLevel.SUPERADMIN;
    }

    /** Returns a copy routed to the supplied page while retaining title/tab state. */
    public TitularScreenState route(Page target) {
        Page next = target == null ? Page.HOME : target;
        Tab nextTab = tab;
        if (next == Page.TITLE_SWITCH) nextTab = Tab.TITLES;
        else if (next == Page.PRIMARY_GROUP) nextTab = Tab.SELF_GROUP;
        else if (next == Page.MANAGEMENT && !isManagementTab(nextTab)) nextTab = Tab.TITLE_EDITOR;
        return new TitularScreenState(revision, selectedTitle, nextTab, next);
    }

    /** Alias for route used by navigation code that treats this as a transition. */
    public TitularScreenState withPage(Page target) { return route(target); }

    private static boolean isManagementTab(Tab tab) {
        return tab == Tab.PLAYERS || tab == Tab.GROUPS || tab == Tab.TITLE_EDITOR || tab == Tab.SETTINGS;
    }

    public static TitularScreenState from(ClientSnapshot snapshot, TitularScreenState previous) {
        if (snapshot == null) return new TitularScreenState(-1L, null, Tab.TITLES, Page.HOME);
        String selected = previous == null ? null : previous.selectedTitle();
        if (selected == null || !snapshot.availableTitleIds().contains(selected)) {
            selected = snapshot.self() == null ? null : snapshot.self().activeTitle();
            if (selected != null && !snapshot.availableTitleIds().contains(selected)) selected = null;
        }
        Tab tab = previous == null ? Tab.TITLES : previous.tab();
        if (!tabs(snapshot.permissionLevel()).contains(tab)) tab = Tab.TITLES;
        Page page = previous == null ? Page.HOME : previous.page();
        if (!pageAllowed(page, snapshot.permissionLevel())) page = Page.HOME;
        return new TitularScreenState(snapshot.revision(), selected, tab, page);
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
