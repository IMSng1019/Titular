package titular.modid.service;

import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;
import titular.modid.model.DisplayMode;
import titular.modid.model.GroupDefinition;
import titular.modid.model.PermissionLevel;
import titular.modid.model.PlayerTitleState;
import titular.modid.model.TitleDefinition;
import titular.modid.model.TitularData;
import titular.modid.model.TitularSettings;
import titular.modid.model.TitularLimits;
import titular.modid.storage.TitularStorage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TitularServiceManagementTest {
    private final UUID admin = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    @Test
    void adminMayChangeOnlyOwnPrimaryGroup() {
        TitularService service = service();

        assertTrue(service.setPrimaryGroup(admin, admin, "staff", PermissionLevel.ADMIN).success());
        assertFalse(service.setPrimaryGroup(admin, other, "staff", PermissionLevel.ADMIN).success());
        assertEquals("staff", service.data().players().get(admin).primaryGroup());
        assertEquals("default", service.data().players().get(other).primaryGroup());
    }

    @Test
    void activationOverloadRejectsCrossPlayerAdminRequests() {
        TitularService service = service();

        assertTrue(service.activateTitle(admin, admin, "vip", PermissionLevel.PLAYER).success());
        assertFalse(service.activateTitle(admin, other, "vip", PermissionLevel.ADMIN).success());
        assertTrue(service.clearActiveTitle(admin, admin, PermissionLevel.PLAYER).success());
        assertFalse(service.clearActiveTitle(admin, other, PermissionLevel.ADMIN).success());
    }

    @Test
    void superadminMayEditOfflinePlayerGroupsAndTitles() {
        TitularService service = service();

        assertTrue(service.setPrimaryGroup(admin, other, "staff", PermissionLevel.SUPERADMIN).success());
        assertTrue(service.setExtraGroups(admin, other, List.of("staff"), PermissionLevel.SUPERADMIN).success());
        assertTrue(service.setExtraTitles(admin, other, List.of("vip"), PermissionLevel.SUPERADMIN).success());
        PlayerTitleState state = service.data().players().get(other);
        assertEquals("staff", state.primaryGroup());
        assertEquals(List.of("staff"), state.extraGroups());
        assertEquals(List.of("vip"), state.extraTitles());
    }

    @Test
    void superadminMayCreateStateForAnOfflinePlayerNotSeenBefore() {
        TitularService service = service();
        UUID offline = UUID.randomUUID();

        assertTrue(service.setPrimaryGroup(admin, offline, "staff", PermissionLevel.SUPERADMIN).success());
        assertTrue(service.setExtraTitles(admin, offline, List.of("vip"), PermissionLevel.SUPERADMIN).success());
        assertEquals("staff", service.data().players().get(offline).primaryGroup());
    }

    @Test
    void playerCannotPerformManagementOperations() {
        TitularService service = service();

        assertFalse(service.setPrimaryGroup(admin, admin, "staff", PermissionLevel.PLAYER).success());
        assertFalse(service.createGroup(admin, new GroupDefinition("new-group"), PermissionLevel.ADMIN).success());
    }

    @Test
    void superadminCanCreateUpdateAndDeleteGroupsAndTitles() {
        TitularService service = service();

        assertTrue(service.createGroup(admin, new GroupDefinition("new-group", "default", List.of("vip")), PermissionLevel.SUPERADMIN).success());
        assertTrue(service.updateGroup(admin, new GroupDefinition("new-group", null, List.of("vip")), PermissionLevel.SUPERADMIN).success());
        assertTrue(service.createTitle(admin, new TitleDefinition("new-title", Text.literal("["), Text.literal("]")), PermissionLevel.SUPERADMIN).success());
        assertTrue(service.deleteTitle(admin, "new-title", PermissionLevel.SUPERADMIN).success());
        assertTrue(service.deleteGroup(admin, "new-group", PermissionLevel.SUPERADMIN).success());
        assertFalse(service.data().groups().containsKey("new-group"));
    }

    @Test
    void groupDeletionRejectsGroupsUsedAsParents() {
        TitularService service = service();

        assertFalse(service.deleteGroup(admin, "parent", PermissionLevel.SUPERADMIN).success());
		assertTrue(service.data().groups().containsKey("parent"));
	}

	@Test
	void groupDeletionRejectsGroupsAssignedToPlayers() {
		TitularService service = service();

		assertFalse(service.deleteGroup(admin, "default", PermissionLevel.SUPERADMIN).success());
		assertTrue(service.data().groups().containsKey("default"));
	}

	@Test
	void extraGroupAndTitleListsRejectBlankReferences() {
		TitularService service = service();

		assertFalse(service.setExtraGroups(admin, admin, List.of(" "), PermissionLevel.SUPERADMIN).success());
		assertFalse(service.setExtraTitles(admin, admin, java.util.Collections.singletonList(null), PermissionLevel.SUPERADMIN).success());
	}

    @Test
    void deletingTitleCleansReferencesAndActiveTitle() {
        TitularService service = service();
        assertTrue(service.activateTitle(admin, "vip").success());

        assertTrue(service.deleteTitle(admin, "vip", PermissionLevel.SUPERADMIN).success());
        PlayerTitleState state = service.data().players().get(admin);
        assertFalse(service.data().groups().get("default").titleIds().contains("vip"));
        assertFalse(state.extraTitles().contains("vip"));
        assertEquals(null, state.activeTitle());
    }

    @Test
    void groupDeletionRejectsAnyPlayerReference() {
        TitularService service = service();

        assertFalse(service.deleteGroup(admin, "default", PermissionLevel.SUPERADMIN).success());
        assertTrue(service.data().groups().containsKey("default"));
    }

    @Test
    void groupDeletionRejectsExtraAndLuckPermsReferences() {
        TitularService service = serviceWithReferences("extra");
        assertFalse(service.deleteGroup(admin, "extra", PermissionLevel.SUPERADMIN).success());

        service = serviceWithLuckPermsReference("lp");
        assertFalse(service.deleteGroup(admin, "lp", PermissionLevel.SUPERADMIN).success());
    }

    @Test
    void extraGroupAndTitleMutationsRejectNullOrBlankReferences() {
        TitularService service = service();

        assertFalse(service.setExtraGroups(admin, other, List.of(" "), PermissionLevel.SUPERADMIN).success());
        assertTrue(service.setExtraGroups(admin, other, null, PermissionLevel.SUPERADMIN).success());
        assertFalse(service.setExtraTitles(admin, other, List.of(""), PermissionLevel.SUPERADMIN).success());
        assertTrue(service.setExtraTitles(admin, other, null, PermissionLevel.SUPERADMIN).success());
        assertFalse(service.addExtraGroup(admin, other, " ", PermissionLevel.SUPERADMIN).success());
        assertFalse(service.addExtraTitle(admin, other, null, PermissionLevel.SUPERADMIN).success());
        assertFalse(service.removeExtraGroup(admin, other, "", PermissionLevel.SUPERADMIN).success());
        assertFalse(service.removeExtraTitle(admin, other, " ", PermissionLevel.SUPERADMIN).success());
    }

    @Test
    void superadminCanChangeSettings() {
        TitularService service = service();

        assertFalse(service.setDisplayMode(admin, DisplayMode.BOTH, PermissionLevel.ADMIN).success());
        assertTrue(service.setDisplayMode(admin, DisplayMode.SUFFIX, PermissionLevel.SUPERADMIN).success());
        assertEquals(DisplayMode.SUFFIX, service.data().settings().displayMode());
    }

    @Test
    void rejectsDefinitionsThatCannotFitTheSnapshotProtocol() {
        Map<String, TitleDefinition> titles = new LinkedHashMap<>();
        for (int i = 0; i < TitularLimits.MAX_DEFINITIONS; i++) {
            titles.put("t" + i, new TitleDefinition("t" + i));
        }
        PlayerTitleState state = new PlayerTitleState(admin, null, List.of(), List.of(), null, List.of());
        TitularData full = new TitularData(titles, Map.of("staff", new GroupDefinition("staff")), Map.of(admin, state), null, 0L);
        TitularService service = new TitularService(new MemoryStorage(full));

        assertFalse(service.createTitle(admin, new TitleDefinition("overflow"), PermissionLevel.SUPERADMIN).success());
    }

    private TitularService service() {
        Map<String, TitleDefinition> titles = new LinkedHashMap<>();
        titles.put("vip", new TitleDefinition("vip"));
        Map<String, GroupDefinition> groups = new LinkedHashMap<>();
        groups.put("default", new GroupDefinition("default", "parent", List.of("vip")));
        groups.put("parent", new GroupDefinition("parent"));
        groups.put("staff", new GroupDefinition("staff", null, List.of("vip")));
        PlayerTitleState first = new PlayerTitleState(admin, "default", List.of(), List.of(), null, List.of());
        PlayerTitleState second = new PlayerTitleState(other, "default", List.of(), List.of(), null, List.of());
        TitularData data = new TitularData(titles, groups, Map.of(admin, first, other, second), new TitularSettings(), 0L);
        return new TitularService(new MemoryStorage(data));
    }

    private TitularService serviceWithReferences(String groupId) {
        Map<String, TitleDefinition> titles = Map.of("vip", new TitleDefinition("vip"));
        Map<String, GroupDefinition> groups = new LinkedHashMap<>();
        groups.put(groupId, new GroupDefinition(groupId));
        PlayerTitleState first = new PlayerTitleState(admin, null, List.of(groupId), List.of(), null, List.of());
        TitularData data = new TitularData(titles, groups, Map.of(admin, first), new TitularSettings(), 0L);
        return new TitularService(new MemoryStorage(data));
    }

    private TitularService serviceWithLuckPermsReference(String groupId) {
        Map<String, TitleDefinition> titles = Map.of("vip", new TitleDefinition("vip"));
        Map<String, GroupDefinition> groups = new LinkedHashMap<>();
        groups.put(groupId, new GroupDefinition(groupId));
        PlayerTitleState first = new PlayerTitleState(admin, null, List.of(), List.of(), null, List.of(groupId));
        TitularData data = new TitularData(titles, groups, Map.of(admin, first), new TitularSettings(), 0L);
        return new TitularService(new MemoryStorage(data));
    }

    private static final class MemoryStorage implements TitularStorage {
        private TitularData data;

        private MemoryStorage(TitularData data) {
            this.data = data;
        }

        @Override
        public TitularData load() {
            return data;
        }

        @Override
        public void save(TitularData data) {
            this.data = data;
        }
    }
}
