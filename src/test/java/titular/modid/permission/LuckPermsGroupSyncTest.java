package titular.modid.permission;

import org.junit.jupiter.api.Test;
import titular.modid.model.GroupDefinition;
import titular.modid.model.PlayerTitleState;
import titular.modid.model.TitularData;
import titular.modid.model.TitleDefinition;
import titular.modid.service.TitularService;
import titular.modid.storage.TitularStorage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LuckPermsGroupSyncTest {
    private static final UUID PLAYER = UUID.randomUUID();

    @Test
    void mirrorsOnlyDefinedGroupsAndPreservesManualState() {
        RecordingStorage storage = new RecordingStorage(data());
        TitularService service = new TitularService(storage, storage.load());
        FakeFacade facade = new FakeFacade(Set.of("known", "missing"));
        AtomicInteger broadcasts = new AtomicInteger();
        LuckPermsIntegration integration = new LuckPermsIntegration(facade, service,
                ignored -> broadcasts.incrementAndGet());

        assertTrue(integration.syncUser(PLAYER));
        PlayerTitleState actual = service.data().players().get(PLAYER);
        assertEquals(List.of("known"), actual.luckPermsGroups());
        assertEquals("manual", actual.primaryGroup());
        assertEquals(List.of("extra"), actual.extraGroups());
        assertEquals(List.of("custom"), actual.extraTitles());
        assertEquals("custom", actual.activeTitle());
        assertEquals(1, storage.saves);
        assertEquals(1, broadcasts.get());
    }

    @Test
    void identicalGroupSetDoesNotSaveOrBroadcast() {
        RecordingStorage storage = new RecordingStorage(dataWithLuckPerms(List.of("known")));
        TitularService service = new TitularService(storage, storage.load());
        FakeFacade facade = new FakeFacade(Set.of("known"));
        AtomicInteger broadcasts = new AtomicInteger();
        LuckPermsIntegration integration = new LuckPermsIntegration(facade, service,
                ignored -> broadcasts.incrementAndGet());

        assertFalse(integration.syncUser(PLAYER));
        assertEquals(0, storage.saves);
        assertEquals(0, broadcasts.get());
    }

    @Test
    void permissionContextDelegatesTrustedLuckPermsNodes() {
        FakeFacade facade = new FakeFacade(Set.of());
        facade.nodes = Set.of("titular.admin", "some.other.node");
        LuckPermsIntegration integration = new LuckPermsIntegration(facade,
                new TitularService(new RecordingStorage(data())), ignored -> { });

        assertEquals(Set.of("titular.admin", "some.other.node"),
                integration.permissionContext(PLAYER).permissionNodes());
        assertEquals(0, integration.permissionContext(PLAYER).operatorLevel());
    }

    @Test
	void recalculationIsScheduledAndListenerCanBeStopped() {
        FakeFacade facade = new FakeFacade(Set.of("known"));
        RecordingStorage storage = new RecordingStorage(data());
        TitularService service = new TitularService(storage, storage.load());
        List<Runnable> queue = new ArrayList<>();
        LuckPermsIntegration integration = new LuckPermsIntegration(facade, service, ignored -> { }, queue::add);

        integration.start();
        facade.fire(PLAYER);
        assertEquals(0, storage.saves);
        queue.remove(0).run();
        assertEquals(1, storage.saves);
        integration.stop();
        int queueSize = queue.size();
        facade.fire(PLAYER);
        assertEquals(queueSize, queue.size());
    }

    @Test
    void noLuckPermsFacadeIsSafeAndEmpty() {
        NoLuckPermsFacade facade = new NoLuckPermsFacade();
        assertTrue(facade.inheritedGroups(PLAYER).isEmpty());
        assertTrue(facade.permissionNodes(PLAYER).isEmpty());
        assertDoesNotThrow(facade::start);
        assertDoesNotThrow(facade::stop);
    }

    @Test
    void mirrorsGroupsForAnOfflineUuidWithNoPreviousTitularState() {
        UUID offline = UUID.randomUUID();
        RecordingStorage storage = new RecordingStorage(data());
        TitularService service = new TitularService(storage, storage.load());

        assertTrue(service.updateLuckPermsGroups(offline, Set.of("known")));
        assertEquals(List.of("known"), service.data().players().get(offline).luckPermsGroups());
        assertNull(service.data().players().get(offline).primaryGroup());
    }

	@Test
	void factoryFallsBackWhenLuckPermsModIsAbsent() {
		if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("luckperms")) {
			assertInstanceOf(NoLuckPermsFacade.class, LuckPermsFacadeFactory.create());
		}
	}

	@Test
	void absentLuckPermsNeverClearsPersistedMirrorGroups() {
		RecordingStorage storage = new RecordingStorage(dataWithLuckPerms(List.of("known")));
		TitularService service = new TitularService(storage, storage.load());
		LuckPermsIntegration integration = new LuckPermsIntegration(new NoLuckPermsFacade(), service, ignored -> { });

		assertFalse(integration.syncUser(PLAYER));
		assertEquals(List.of("known"), service.data().players().get(PLAYER).luckPermsGroups());
		assertEquals(0, storage.saves);
	}

	@Test
	void queuedCallbackFromStoppedIntegrationCannotMutateDetachedService() {
		FakeFacade facade = new FakeFacade(Set.of("known"));
		RecordingStorage storage = new RecordingStorage(data());
		List<Runnable> queue = new ArrayList<>();
		LuckPermsIntegration integration = new LuckPermsIntegration(facade,
			new TitularService(storage, storage.load()), ignored -> { }, queue::add);

		integration.start();
		facade.fire(PLAYER);
		integration.stop();
		queue.remove(0).run();

		assertEquals(0, storage.saves);
	}

    private static TitularData data() {
        return dataWithLuckPerms(List.of());
    }

    private static TitularData dataWithLuckPerms(List<String> lpGroups) {
        MapBuilder builder = new MapBuilder();
        builder.titles.put("custom", new TitleDefinition("custom", null, null));
        builder.groups.put("manual", new GroupDefinition("manual", null, List.of("custom")));
        builder.groups.put("extra", new GroupDefinition("extra", null, List.of()));
        builder.groups.put("known", new GroupDefinition("known", null, List.of()));
        builder.players.put(PLAYER, new PlayerTitleState(PLAYER, "manual", List.of("extra"),
                List.of("custom"), "custom", lpGroups));
        return new TitularData(builder.titles, builder.groups, builder.players, null, 0);
    }

    private static final class MapBuilder {
        final LinkedHashMap<String, TitleDefinition> titles = new LinkedHashMap<>();
        final LinkedHashMap<String, GroupDefinition> groups = new LinkedHashMap<>();
        final LinkedHashMap<UUID, PlayerTitleState> players = new LinkedHashMap<>();
    }

    private static final class RecordingStorage implements TitularStorage {
        TitularData data;
        int saves;
        RecordingStorage(TitularData data) { this.data = data; }
        @Override public TitularData load() { return data; }
        @Override public void save(TitularData data) { saves++; this.data = data; }
    }

    private static final class FakeFacade implements LuckPermsFacade {
        Set<String> groups;
        Set<String> nodes = Set.of();
        java.util.function.Consumer<UUID> listener;
        FakeFacade(Set<String> groups) { this.groups = groups; }
        @Override public Set<String> inheritedGroups(UUID playerId) { return groups; }
        @Override public Set<String> permissionNodes(UUID actorId) { return nodes; }
        @Override public void registerUserDataRecalculationListener(java.util.function.Consumer<UUID> listener) {
            this.listener = listener;
        }
        @Override public void unregisterUserDataRecalculationListener(java.util.function.Consumer<UUID> listener) {
            if (this.listener == listener) this.listener = null;
        }
        void fire(UUID id) { if (listener != null) listener.accept(id); }
    }
}
