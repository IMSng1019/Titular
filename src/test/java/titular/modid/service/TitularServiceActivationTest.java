package titular.modid.service;

import org.junit.jupiter.api.Test;
import titular.modid.model.GroupDefinition;
import titular.modid.model.PlayerTitleState;
import titular.modid.model.TitleDefinition;
import titular.modid.model.TitularData;
import titular.modid.model.PermissionLevel;
import titular.modid.storage.TitularStorage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TitularServiceActivationTest {
	private final UUID playerId = UUID.randomUUID();

	@Test
	void activatesAnAvailableTitleAndPersistsTheNewSnapshot() {
		PlayerTitleState state = player("group", null);
		MemoryStorage storage = new MemoryStorage(data(state));
		TitularService service = new TitularService(storage);

		MutationResult result = service.activateTitle(playerId, "child");

		assertTrue(result.success());
		assertEquals("child", service.data().players().get(playerId).activeTitle());
		assertEquals("child", storage.saved.players().get(playerId).activeTitle());
		assertEquals(1L, service.data().revision());
	}

	@Test
	void rejectsAnUnavailableTitleWithoutChangingState() {
		PlayerTitleState state = player("group", null);
		MemoryStorage storage = new MemoryStorage(data(state));
		TitularService service = new TitularService(storage);

		MutationResult result = service.activateTitle(playerId, "not-in-pool");

		assertFalse(result.success());
		assertEquals(null, service.data().players().get(playerId).activeTitle());
		assertEquals(0L, service.data().revision());
		assertEquals(0, storage.saveCount);
	}

	@Test
	void clearActivationRemovesTheActiveTitle() {
		PlayerTitleState state = player("group", "child");
		MemoryStorage storage = new MemoryStorage(data(state));
		TitularService service = new TitularService(storage);

		MutationResult result = service.clearActiveTitle(playerId);

		assertTrue(result.success());
		assertEquals(null, service.data().players().get(playerId).activeTitle());
	}

	@Test
	void invalidPersistedActiveTitleIsNotVisibleAsAnActiveDefinition() {
		PlayerTitleState state = player("group", "deleted-title");
		TitularService service = new TitularService(new MemoryStorage(data(state)));

		assertTrue(service.resolveActiveTitle(playerId).isEmpty());
	}

	@Test
	void playerCannotActivateAnotherPlayersTitle() {
		UUID other = UUID.randomUUID();
		PlayerTitleState state = player("group", null);
		PlayerTitleState otherState = new PlayerTitleState(other, "group", List.of(), List.of(), null, List.of());
		Map<UUID, PlayerTitleState> players = new LinkedHashMap<>();
		players.put(playerId, state);
		players.put(other, otherState);
		TitularData initial = new TitularData(data(state).titles(), data(state).groups(), players, null, 0L);
		TitularService service = new TitularService(new MemoryStorage(initial));

		assertFalse(service.activateTitle(playerId, other, "child", PermissionLevel.PLAYER).success());
		assertEquals(null, service.data().players().get(other).activeTitle());
	}

	@Test
	void ensurePlayerCreatesAnEmptyPersistedStateForFirstJoin() {
		UUID firstJoin = UUID.randomUUID();
		MemoryStorage storage = new MemoryStorage(data(player("group", null)));
		TitularService service = new TitularService(storage, storage.load());

		assertEquals(firstJoin, service.ensurePlayer(firstJoin).playerId());
		assertTrue(service.data().players().containsKey(firstJoin));
	}

	@Test
	void storageFailureLeavesTheAuthoritativeStateUnchanged() {
		PlayerTitleState state = player("group", null);
		FailingStorage storage = new FailingStorage(data(state));
		TitularService service = new TitularService(storage, storage.load());

		assertThrows(RuntimeException.class, () -> service.activateTitle(playerId, "child"));
		assertEquals(null, service.data().players().get(playerId).activeTitle());
		assertEquals(0L, service.data().revision());
	}

	private PlayerTitleState player(String group, String active) {
		return new PlayerTitleState(playerId, group, List.of(), List.of(), active, List.of());
	}

	private TitularData data(PlayerTitleState state) {
		Map<String, TitleDefinition> titles = new LinkedHashMap<>();
		titles.put("child", new TitleDefinition("child"));
		titles.put("other", new TitleDefinition("other"));
		Map<String, GroupDefinition> groups = Map.of("group", new GroupDefinition("group", null, List.of("child")));
		return new TitularData(titles, groups, Map.of(playerId, state), null, 0L);
	}

	private static class MemoryStorage implements TitularStorage {
		private TitularData value;
		private TitularData saved;
		private int saveCount;

		private MemoryStorage(TitularData value) {
			this.value = value;
		}

		@Override
		public TitularData load() {
			return value;
		}

		@Override
		public void save(TitularData data) {
			saveCount++;
			saved = data;
			value = data;
		}
	}

	private static final class FailingStorage extends MemoryStorage {
		private FailingStorage(TitularData value) { super(value); }
		@Override public void save(TitularData data) { throw new IllegalStateException("disk full"); }
	}
}
