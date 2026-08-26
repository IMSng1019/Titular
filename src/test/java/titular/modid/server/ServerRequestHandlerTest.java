package titular.modid.server;

import org.junit.jupiter.api.Test;
import titular.modid.model.GroupDefinition;
import titular.modid.model.PlayerTitleState;
import titular.modid.model.TitleDefinition;
import titular.modid.model.TitularData;
import titular.modid.network.TitularRequest;
import titular.modid.permission.PermissionResolver;
import titular.modid.service.TitularService;
import titular.modid.storage.TitularStorage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerRequestHandlerTest {
	@Test
	void rejectsStaleRevisionWithoutBroadcasting() {
		UUID actor = UUID.randomUUID();
		RecordingHooks hooks = new RecordingHooks();
		ServerRequestHandler handler = new ServerRequestHandler(service(actor), hooks, hooks,
			new ServerRequestHandler.ControlCallbacks() {
				@Override public titular.modid.service.MutationResult refresh(UUID ignored) { return titular.modid.service.MutationResult.accepted(service(actor).data()); }
				@Override public titular.modid.service.MutationResult reload(UUID ignored) { return titular.modid.service.MutationResult.accepted(service(actor).data()); }
			});

		assertFalse(handler.handle(actor, TitularRequest.setPrimaryGroup("staff", 99L)).success());
		assertEquals(0, hooks.successes);
		assertEquals(1, hooks.errors.size());
	}

	@Test
	void routesValidatedMutationThenBroadcastsAfterSave() {
		UUID actor = UUID.randomUUID();
		RecordingHooks hooks = new RecordingHooks();
		MemoryStorage storage = new MemoryStorage(data(actor));
		TitularService service = new TitularService(storage, new titular.modid.permission.VanillaPermissionResolver(),
			ignored -> new PermissionResolver.PermissionContext(2, Set.of()));
		ServerRequestHandler handler = new ServerRequestHandler(service, hooks, hooks,
			new ServerRequestHandler.ControlCallbacks() {
				@Override public titular.modid.service.MutationResult refresh(UUID ignored) { return titular.modid.service.MutationResult.accepted(service.data()); }
				@Override public titular.modid.service.MutationResult reload(UUID ignored) { return titular.modid.service.MutationResult.accepted(service.data()); }
			});

		assertTrue(handler.handle(actor, TitularRequest.setPrimaryGroup("staff", 0L)).success());
		assertEquals(1, hooks.successes);
		assertEquals("staff", storage.data.players().get(actor).primaryGroup());
	}

	@Test
	void unauthorizedClientCannotEscalateThroughRequestPayload() {
		UUID actor = UUID.randomUUID();
		RecordingHooks hooks = new RecordingHooks();
		TitularService service = service(actor);
		ServerRequestHandler handler = new ServerRequestHandler(service, hooks, hooks,
			new ServerRequestHandler.ControlCallbacks() {
				@Override public titular.modid.service.MutationResult refresh(UUID ignored) { return titular.modid.service.MutationResult.accepted(service.data()); }
				@Override public titular.modid.service.MutationResult reload(UUID ignored) { return titular.modid.service.MutationResult.accepted(service.data()); }
			});

		assertFalse(handler.handle(actor, TitularRequest.createGroup(new GroupDefinition("evil"), 0L)).success());
	}

	@Test
	void rejectsMutationWithoutARevisionToken() {
		UUID actor = UUID.randomUUID();
		RecordingHooks hooks = new RecordingHooks();
		TitularService service = service(actor);
		ServerRequestHandler handler = new ServerRequestHandler(service, hooks, hooks,
			new ServerRequestHandler.ControlCallbacks() {
				@Override public titular.modid.service.MutationResult refresh(UUID ignored) { return titular.modid.service.MutationResult.accepted(service.data()); }
				@Override public titular.modid.service.MutationResult reload(UUID ignored) { return titular.modid.service.MutationResult.accepted(service.data()); }
			});

		assertFalse(handler.handle(actor, TitularRequest.clear(-1L)).success());
		assertEquals(0L, service.data().revision());
	}

	private static TitularService service(UUID actor) {
		return new TitularService(new MemoryStorage(data(actor)), new titular.modid.permission.VanillaPermissionResolver(),
			ignored -> new PermissionResolver.PermissionContext(0, Set.of()));
	}

	private static TitularData data(UUID actor) {
		return new TitularData(Map.of("title", new TitleDefinition("title")),
			Map.of("staff", new GroupDefinition("staff")),
			Map.of(actor, new PlayerTitleState(actor, null, List.of(), List.of(), null, List.of())), null, 0L);
	}

	private static final class RecordingHooks implements SnapshotBroadcaster, ErrorResponder {
		int successes;
		final List<String> errors = new ArrayList<>();

		@Override public void broadcast(TitularData data) { successes++; }
		@Override public void error(UUID actor, String message, boolean refresh) { errors.add(message); }
	}

	private static final class MemoryStorage implements TitularStorage {
		TitularData data;
		MemoryStorage(TitularData data) { this.data = data; }
		@Override public TitularData load() { return data; }
		@Override public void save(TitularData data) { this.data = data; }
	}
}
