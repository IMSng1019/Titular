package titular.modid.model;

import java.util.List;
import java.util.Objects;

public record GroupDefinition(String id, String parent, List<String> titleIds) {
	public GroupDefinition {
		id = Objects.requireNonNull(id, "id");
		titleIds = titleIds == null ? List.of() : List.copyOf(titleIds);
	}

	public GroupDefinition(String id) {
		this(id, null, null);
	}
}
