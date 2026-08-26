package titular.modid.model;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record PlayerTitleState(
	UUID playerId,
	String primaryGroup,
	List<String> extraGroups,
	List<String> extraTitles,
	String activeTitle,
	List<String> luckPermsGroups
) {
	public PlayerTitleState {
		playerId = Objects.requireNonNull(playerId, "playerId");
		extraGroups = extraGroups == null ? List.of() : List.copyOf(extraGroups);
		extraTitles = extraTitles == null ? List.of() : List.copyOf(extraTitles);
		luckPermsGroups = luckPermsGroups == null ? List.of() : List.copyOf(luckPermsGroups);
	}

	public PlayerTitleState(UUID playerId) {
		this(playerId, null, null, null, null, null);
	}
}
