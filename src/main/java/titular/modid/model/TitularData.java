package titular.modid.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record TitularData(
	Map<String, TitleDefinition> titles,
	Map<String, GroupDefinition> groups,
	Map<UUID, PlayerTitleState> players,
	TitularSettings settings,
	long revision
) {
	public TitularData {
		titles = immutableMap(titles);
		groups = immutableMap(groups);
		players = immutableMap(players);
		settings = settings == null ? new TitularSettings() : settings;
	}

	public TitularData() {
		this(null, null, null, null, 0L);
	}

	private static <K, V> Map<K, V> immutableMap(Map<K, V> source) {
		if (source == null || source.isEmpty()) {
			return Map.of();
		}
		return Collections.unmodifiableMap(new LinkedHashMap<>(source));
	}
}
