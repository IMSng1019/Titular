package titular.modid.service;

import org.junit.jupiter.api.Test;
import titular.modid.model.GroupDefinition;
import titular.modid.model.PlayerTitleState;
import titular.modid.model.TitleDefinition;
import titular.modid.model.TitularData;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TitlePoolResolverTest {
	private final UUID playerId = UUID.randomUUID();

	@Test
	void resolvesParentChainAndDeduplicatesTitlesInFirstSeenOrder() {
		Map<String, TitleDefinition> titles = titles("child", "shared", "parent", "extra", "direct");
		Map<String, GroupDefinition> groups = new LinkedHashMap<>();
		groups.put("child-group", new GroupDefinition("child-group", "parent-group", List.of("child", "shared")));
		groups.put("parent-group", new GroupDefinition("parent-group", null, List.of("shared", "parent")));
		groups.put("extra-group", new GroupDefinition("extra-group", null, List.of("extra", "shared")));

		PlayerTitleState state = new PlayerTitleState(playerId, "child-group", List.of("extra-group"), List.of("direct"), null, List.of());

		assertEquals(List.of("child", "shared", "parent", "extra", "direct"),
				new TitlePoolResolver().resolve(state, new TitularData(titles, groups, Map.of(playerId, state), null, 0L)));
	}

	@Test
	void skipsMissingReferencesAndStopsCycles() {
		Map<String, TitleDefinition> titles = titles("a", "b", "c");
		Map<String, GroupDefinition> groups = new LinkedHashMap<>();
		groups.put("a-group", new GroupDefinition("a-group", "b-group", List.of("a", "missing-title")));
		groups.put("b-group", new GroupDefinition("b-group", "a-group", List.of("b", "c")));

		PlayerTitleState state = new PlayerTitleState(playerId, "a-group", List.of("missing-group"), List.of("missing-direct", "c"), null, List.of());

		assertEquals(List.of("a", "b", "c"), new TitlePoolResolver().resolve(state,
				new TitularData(titles, groups, Map.of(playerId, state), null, 0L)));
	}

	@Test
	void includesLuckPermsGroupsAfterManualGroups() {
		Map<String, TitleDefinition> titles = titles("primary", "manual", "luck", "direct");
		Map<String, GroupDefinition> groups = new LinkedHashMap<>();
		groups.put("primary-group", new GroupDefinition("primary-group", null, List.of("primary")));
		groups.put("manual-group", new GroupDefinition("manual-group", null, List.of("manual")));
		groups.put("luck-group", new GroupDefinition("luck-group", null, List.of("luck")));

		PlayerTitleState state = new PlayerTitleState(playerId, "primary-group", List.of("manual-group"), List.of("direct"), null, List.of("luck-group"));

		assertEquals(List.of("primary", "manual", "luck", "direct"), new TitlePoolResolver().resolve(state,
				new TitularData(titles, groups, Map.of(playerId, state), null, 0L)));
	}

	private static Map<String, TitleDefinition> titles(String... ids) {
		Map<String, TitleDefinition> result = new LinkedHashMap<>();
		for (String id : ids) result.put(id, new TitleDefinition(id));
		return result;
	}
}
