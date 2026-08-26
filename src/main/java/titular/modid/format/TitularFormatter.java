package titular.modid.format;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import titular.modid.model.DisplayMode;
import titular.modid.model.TitleDefinition;

import java.util.Objects;

/** Builds the visual player-name representation used by all display surfaces. */
public final class TitularFormatter {
	private TitularFormatter() {
	}

	/**
	 * Formats a player name without changing either the name or title definition.
	 * Every emitted component is copied before it is attached to the fresh root.
	 */
	public static MutableText format(Text originalName, TitleDefinition activeTitle, DisplayMode mode) {
		Objects.requireNonNull(originalName, "originalName");
		DisplayMode effectiveMode = mode == null ? DisplayMode.PREFIX : mode;
		MutableText result = Text.empty();

		Text prefix = activeTitle == null ? null : activeTitle.prefix();
		Text suffix = activeTitle == null ? null : activeTitle.suffix();
		if (activeTitle != null && (effectiveMode == DisplayMode.PREFIX || effectiveMode == DisplayMode.BOTH)) {
			appendIfVisible(result, prefix);
		}
		result.append(originalName.copy());
		if (activeTitle != null && (effectiveMode == DisplayMode.SUFFIX || effectiveMode == DisplayMode.BOTH)) {
			appendIfVisible(result, suffix);
		}
		return result;
	}

	private static void appendIfVisible(MutableText result, Text component) {
		if (component != null && !component.getString().isEmpty()) {
			result.append(component.copy());
		}
	}
}
