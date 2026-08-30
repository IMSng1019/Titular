package titular.modid.client;

import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;
import titular.modid.model.DisplayMode;
import titular.modid.model.PermissionLevel;
import titular.modid.model.PlayerTitleState;
import titular.modid.network.ClientSnapshot;
import titular.modid.network.TitularRequest;
import titular.modid.client.screen.LandingAction;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TitularScreenStateTest {
    private static final UUID PLAYER = UUID.randomUUID();

    @Test
    void permissionProjectsOnlyTheTabsAllowedBySnapshot() {
        assertEquals(List.of(TitularScreenState.Tab.TITLES), TitularScreenState.tabs(PermissionLevel.PLAYER));
        assertEquals(List.of(TitularScreenState.Tab.TITLES, TitularScreenState.Tab.SELF_GROUP),
                TitularScreenState.tabs(PermissionLevel.ADMIN));
        assertEquals(List.of(TitularScreenState.Tab.TITLES, TitularScreenState.Tab.SELF_GROUP,
                        TitularScreenState.Tab.PLAYERS, TitularScreenState.Tab.GROUPS,
                        TitularScreenState.Tab.TITLE_EDITOR, TitularScreenState.Tab.SETTINGS),
                TitularScreenState.tabs(PermissionLevel.SUPERADMIN));
    }

    @Test
    void selectionIsRetainedAcrossSnapshotRevisionsOnlyWhenStillVisible() {
        ClientSnapshot first = snapshot(7, List.of("red", "blue"), "blue");
        TitularScreenState state = TitularScreenState.from(first, null);
        assertEquals("blue", state.selectedTitle());

        ClientSnapshot next = snapshot(8, List.of("blue", "green"), "green");
        state = TitularScreenState.from(next, state);
        assertEquals(8, state.revision());
        assertEquals("blue", state.selectedTitle());

        ClientSnapshot removed = snapshot(9, List.of("green"), "green");
        assertEquals("green", TitularScreenState.from(removed, state).selectedTitle());
    }

    @Test
    void requestsAreBuiltAgainstTheSnapshotRevision() {
        ClientSnapshot snapshot = snapshot(42, List.of("one"), "one");
        assertEquals(TitularRequest.activate("one", 42), TitularScreenState.activateRequest(snapshot, "one"));
        assertEquals(TitularRequest.clear(42), TitularScreenState.clearRequest(snapshot));
        assertEquals(TitularRequest.setPrimaryGroup("staff", 42),
                TitularScreenState.primaryGroupRequest(snapshot, "staff"));
    }

    @Test
    void noActiveTitleDoesNotPreselectTheFirstAvailableTitle() {
        ClientSnapshot snapshot = snapshot(5, List.of("one", "two"), null);
        assertNull(TitularScreenState.from(snapshot, null).selectedTitle());
    }

    @Test
    void landingActionsAreProjectedByPermission() {
        assertEquals(List.of(LandingAction.SWITCH_TITLE, LandingAction.LANGUAGE),
                TitularScreenState.actions(PermissionLevel.PLAYER));
        assertEquals(List.of(LandingAction.SWITCH_TITLE, LandingAction.SELECT_PRIMARY_GROUP,
                        LandingAction.LANGUAGE),
                TitularScreenState.actions(PermissionLevel.ADMIN));
        assertEquals(List.of(LandingAction.SWITCH_TITLE, LandingAction.SELECT_PRIMARY_GROUP,
                        LandingAction.MANAGE_TITLES, LandingAction.LANGUAGE),
                TitularScreenState.actions(PermissionLevel.SUPERADMIN));
    }

    @Test
    void routeStartsAtHomeAndSurvivesSnapshotRevisionChanges() {
        ClientSnapshot first = snapshot(1, List.of("red", "blue"), "blue");
        TitularScreenState state = TitularScreenState.from(first, null);
        assertEquals(TitularScreenState.Page.HOME, state.page());
        state = state.route(TitularScreenState.Page.TITLE_SWITCH);
        assertEquals(TitularScreenState.Page.TITLE_SWITCH, state.page());
        assertEquals(TitularScreenState.Tab.TITLES, state.tab());

        ClientSnapshot next = snapshot(2, List.of("blue", "green"), "green");
        TitularScreenState retained = TitularScreenState.from(next, state);
        assertEquals(TitularScreenState.Page.TITLE_SWITCH, retained.page());
        assertEquals(TitularScreenState.Tab.TITLES, retained.tab());
        assertEquals("blue", retained.selectedTitle());
    }

    private static ClientSnapshot snapshot(long revision, List<String> titles, String active) {
        return new ClientSnapshot(revision, DisplayMode.PREFIX,
                new PlayerTitleState(PLAYER, null, List.of(), List.of(), active, List.of()), titles, List.of(),
                PermissionLevel.PLAYER);
    }
}
