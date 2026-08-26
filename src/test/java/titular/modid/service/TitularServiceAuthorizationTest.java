package titular.modid.service;

import org.junit.jupiter.api.Test;
import titular.modid.model.DisplayMode;
import titular.modid.model.GroupDefinition;
import titular.modid.model.PermissionLevel;
import titular.modid.model.PlayerTitleState;
import titular.modid.model.TitleDefinition;
import titular.modid.model.TitularData;
import titular.modid.permission.PermissionResolver;
import titular.modid.storage.TitularStorage;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TitularServiceAuthorizationTest {
	@Test
	void publicMutationsResolvePermissionFromTheInjectedActorProvider() {
		UUID actor = UUID.randomUUID();
		PermissionResolver resolver = new titular.modid.permission.VanillaPermissionResolver();
		TitularService service = new TitularService(new MemoryStorage(data(actor)), resolver,
			ignored -> new PermissionResolver.PermissionContext(0, Set.of("titular.admin")));

		assertFalse(service.setDisplayMode(actor, DisplayMode.BOTH).success());
		assertTrue(service.setPrimaryGroup(actor, actor, "staff").success());
	}

	@Test
	void clientCannotSelectSuperadminThroughPublicApi() {
		UUID actor = UUID.randomUUID();
		TitularService service = new TitularService(new MemoryStorage(data(actor)),
			new titular.modid.permission.VanillaPermissionResolver(), ignored -> new PermissionResolver.PermissionContext(0, Set.of()));

		assertFalse(service.setDisplayMode(actor, DisplayMode.BOTH).success());
		assertTrue(service.data().settings().displayMode() != DisplayMode.BOTH);
	}

	private static TitularData data(UUID actor) {
		return new TitularData(
			Map.of("title", new TitleDefinition("title")),
			Map.of("staff", new GroupDefinition("staff")),
			Map.of(actor, new PlayerTitleState(actor, null, List.of(), List.of(), null, List.of())),
			null,
			0L);
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
