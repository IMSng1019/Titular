package titular.modid.network;

import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;
import titular.modid.model.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SnapshotProjectionTest {
    private static final UUID SELF = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();

    private TitularData data() {
        Map<String, TitleDefinition> titles = new LinkedHashMap<>();
        titles.put("vip", new TitleDefinition("vip", Text.literal("["), Text.literal("]")));
        titles.put("mod", new TitleDefinition("mod", Text.literal("<"), Text.literal(">")));
        Map<String, GroupDefinition> groups = new LinkedHashMap<>();
        groups.put("default", new GroupDefinition("default", null, List.of("vip")));
        groups.put("staff", new GroupDefinition("staff", "default", List.of("mod")));
        Map<UUID, PlayerTitleState> players = Map.of(
                SELF, new PlayerTitleState(SELF, "staff", List.of(), List.of(), "mod", List.of()),
                OTHER, new PlayerTitleState(OTHER, "default", List.of(), List.of(), "vip", List.of()));
        return new TitularData(titles, groups, players, new TitularSettings(DisplayMode.SUFFIX), 7L);
    }

    @Test
    void playerProjectionOnlyExposesOwnStateAndDisplayEntries() {
        ClientSnapshot snapshot = SnapshotProjector.project(data(), SELF, PermissionLevel.PLAYER,
                List.of(new SnapshotProjector.OnlinePlayer(OTHER, Text.literal("Other"))));
        assertEquals(PermissionLevel.PLAYER, snapshot.permissionLevel());
        assertTrue(snapshot.management().isEmpty());
        assertTrue(snapshot.groups().isEmpty());
        assertTrue(snapshot.titles().isEmpty());
        assertTrue(snapshot.playerStates().isEmpty());
        assertEquals("staff", snapshot.self().primaryGroup());
        assertEquals(List.of("mod", "vip"), snapshot.availableTitleIds());
        assertEquals("vip", snapshot.onlinePlayers().get(0).activeTitle().id());
    }

    @Test
    void adminProjectionOnlyIncludesGroupIds() {
        ClientSnapshot snapshot = SnapshotProjector.project(data(), SELF, PermissionLevel.ADMIN, List.of());
        assertTrue(snapshot.management().isPresent());
        assertEquals(List.of("default", "staff"), snapshot.management().get().groupIds());
        assertTrue(snapshot.groups().isEmpty());
        assertTrue(snapshot.titles().isEmpty());
        assertTrue(snapshot.playerStates().isEmpty());
        assertTrue(snapshot.management().get().titleIds().isEmpty());
        assertTrue(snapshot.management().get().playerIds().isEmpty());
        assertNull(snapshot.management().get().settings());
        assertTrue(snapshot.canManageSelfGroup());
        assertFalse(snapshot.canManageAll());
    }

    @Test
    void superadminProjectionIncludesDefinitionsAndAllPlayers() {
        ClientSnapshot snapshot = SnapshotProjector.project(data(), SELF, PermissionLevel.SUPERADMIN, List.of());
        assertEquals(2, snapshot.groups().size());
        assertEquals(2, snapshot.titles().size());
        assertEquals(2, snapshot.playerStates().size());
        assertTrue(snapshot.management().isPresent());
        assertTrue(snapshot.canManageSelfGroup());
        assertTrue(snapshot.canManageAll());
    }

    @Test
    void clientSnapshotDerivesManagementFlagsFromPermissionLevel() {
        ClientSnapshot snapshot = new ClientSnapshot(1L, DisplayMode.PREFIX, null, List.of(), List.of(),
                PermissionLevel.PLAYER, true, true, new ClientSnapshot.ManagementData(List.of("group"), null, null, null));
        assertFalse(snapshot.canManageSelfGroup());
        assertFalse(snapshot.canManageAll());
        assertTrue(snapshot.management().isEmpty());
    }
}
