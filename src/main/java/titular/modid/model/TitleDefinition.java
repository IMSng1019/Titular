package titular.modid.model;

import net.minecraft.text.Text;

import java.util.Objects;

public record TitleDefinition(String id, Text prefix, Text suffix) {
	public TitleDefinition {
		id = Objects.requireNonNull(id, "id");
		prefix = copyOrEmpty(prefix);
		suffix = copyOrEmpty(suffix);
	}

	public TitleDefinition(String id) {
		this(id, null, null);
	}

	@Override
	public Text prefix() {
		return prefix.copy();
	}

	@Override
	public Text suffix() {
		return suffix.copy();
	}

	private static Text copyOrEmpty(Text text) {
		return text == null ? Text.empty() : text.copy();
	}
}
