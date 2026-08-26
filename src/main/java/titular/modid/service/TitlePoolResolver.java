package titular.modid.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import titular.modid.model.GroupDefinition;
import titular.modid.model.PlayerTitleState;
import titular.modid.model.TitularData;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Resolves a player's title IDs while preserving configuration order. */
public final class TitlePoolResolver {
	private static final Logger LOGGER = LoggerFactory.getLogger("titular-service");

	public List<String> resolve(UUID playerId, TitularData data) {
		if (playerId == null || data == null) return List.of();
		PlayerTitleState state = data.players().get(playerId);
		return state == null ? List.of() : resolve(state, data);
	}

	public List<String> resolve(PlayerTitleState state, TitularData data) {
		if (state == null || data == null) return List.of();
		Set<String> resolved = new LinkedHashSet<>();
		resolveGroupChain(state.primaryGroup(), data, resolved);
		for (String groupId : state.extraGroups()) resolveGroupChain(groupId, data, resolved);
		for (String groupId : state.luckPermsGroups()) resolveGroupChain(groupId, data, resolved);
		for (String titleId : state.extraTitles()) addTitle(titleId, data, resolved);
		return List.copyOf(resolved);
	}

	private void resolveGroupChain(String rootId, TitularData data, Set<String> resolved) {
		if (rootId == null || rootId.isBlank()) return;
		Set<String> visited = new LinkedHashSet<>();
		String groupId = rootId;
		while (groupId != null && !groupId.isBlank()) {
			if (!visited.add(groupId)) {
				LOGGER.warn("Cycle detected in Titular group inheritance at {}", groupId);
				return;
			}
			GroupDefinition group = data.groups().get(groupId);
			if (group == null) {
				LOGGER.warn("Missing Titular group reference {}", groupId);
				return;
			}
			for (String titleId : group.titleIds()) addTitle(titleId, data, resolved);
			groupId = group.parent();
		}
	}

	private void addTitle(String titleId, TitularData data, Set<String> resolved) {
		if (titleId == null || titleId.isBlank()) return;
		if (!data.titles().containsKey(titleId)) {
			LOGGER.warn("Missing Titular title reference {}", titleId);
			return;
		}
		resolved.add(titleId);
	}
}
