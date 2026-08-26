package titular.modid.codec;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.text.MutableText;
import net.minecraft.text.PlainTextContent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import titular.modid.model.TitularLimits;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * JSON codec for the literal, styled runs used by titular title editing.
 *
 * <p>The vanilla text codec also supports registry-backed click and hover
 * events. Those components are deliberately outside this configuration
 * format: rejecting them prevents a title file from silently losing visual
 * or interactive data and keeps this codec usable before Minecraft registries
 * have been bootstrapped.</p>
 */
public final class TextJsonCodec {
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
	private static final int MAX_DEPTH = 64;
	private static final int MAX_NODES = 2048;

	private TextJsonCodec() {
	}

	public static String encode(Text text) {
		if (text == null) {
			throw new IllegalArgumentException("Text cannot be null");
		}
		try {
			String encoded = GSON.toJson(encodeNode(text, Style.EMPTY, new IdentityHashMap<>(), new NodeBudget(), 1));
			if (encoded.length() > TitularLimits.MAX_TEXT_JSON_LENGTH) {
				throw new IllegalArgumentException("Text JSON exceeds maximum length of " + TitularLimits.MAX_TEXT_JSON_LENGTH);
			}
			return encoded;
		} catch (StackOverflowError error) {
			throw new IllegalArgumentException("Text tree is too deep", error);
		}
	}

	public static Text decode(String json) {
		if (json == null) {
			throw new IllegalArgumentException("Text JSON cannot be null");
		}
		if (json.length() > TitularLimits.MAX_TEXT_JSON_LENGTH) {
			throw new IllegalArgumentException("Text JSON exceeds maximum length of " + TitularLimits.MAX_TEXT_JSON_LENGTH);
		}

		final JsonElement element;
		try {
			element = JsonParser.parseString(json);
		} catch (RuntimeException | StackOverflowError exception) {
			throw new IllegalArgumentException("Invalid Text JSON", exception);
		}
		if (!element.isJsonObject()) {
			throw new IllegalArgumentException("Text JSON must be an object");
		}
		try {
			return decodeNode(element.getAsJsonObject(), new NodeBudget(), 1);
		} catch (StackOverflowError error) {
			throw new IllegalArgumentException("Text tree is too deep", error);
		}
	}

	private static JsonObject encodeNode(Text text, Style parentStyle, IdentityHashMap<Text, Boolean> path,
			NodeBudget budget, int depth) {
		budget.visit(depth);
		if (path.put(text, Boolean.TRUE) != null) {
			throw new IllegalArgumentException("Text contains a cycle");
		}
		try {
			if (!(text.getContent() instanceof PlainTextContent content)) {
				throw new IllegalArgumentException("Only literal text components are supported");
			}
			Style style = text.getStyle().withParent(parentStyle);
			validateStyle(style);

			JsonObject object = new JsonObject();
			object.addProperty("text", content.string());
			if (style.getColor() != null) {
				object.addProperty("color", style.getColor().getName());
			}
			if (style.isBold()) {
				object.addProperty("bold", true);
			}
			if (style.isItalic()) {
				object.addProperty("italic", true);
			}
			if (style.isUnderlined()) {
				object.addProperty("underlined", true);
			}
			if (style.isStrikethrough()) {
				object.addProperty("strikethrough", true);
			}

			if (!text.getSiblings().isEmpty()) {
				JsonArray siblings = new JsonArray();
				for (Text sibling : text.getSiblings()) {
					if (sibling == null) {
						throw new IllegalArgumentException("Text contains a null sibling");
					}
					siblings.add(encodeNode(sibling, style, path, budget, depth + 1));
				}
				object.add("extra", siblings);
			}
			return object;
		} finally {
			path.remove(text);
		}
	}

	private static void validateStyle(Style style) {
		if (style == null) {
			throw new IllegalArgumentException("Text has no style");
		}
		if (style.isObfuscated()
				|| style.getClickEvent() != null
				|| style.getHoverEvent() != null
				|| style.getInsertion() != null
				|| !Style.DEFAULT_FONT_ID.equals(style.getFont())) {
			throw new IllegalArgumentException("Unsupported text style component");
		}
	}

	private static MutableText decodeNode(JsonObject object, NodeBudget budget, int depth) {
		budget.visit(depth);
		String text = "";
		boolean hasText = false;
		Style style = Style.EMPTY;
		JsonArray siblings = null;

		for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
			String key = entry.getKey();
			JsonElement value = entry.getValue();
			switch (key) {
				case "text" -> { text = readString(value, "text"); hasText = true; }
				case "color" -> style = style.withColor(readColor(value));
				case "bold" -> style = style.withBold(readBoolean(value, "bold"));
				case "italic" -> style = style.withItalic(readBoolean(value, "italic"));
				case "underlined" -> style = style.withUnderline(readBoolean(value, "underlined"));
				case "strikethrough" -> style = style.withStrikethrough(readBoolean(value, "strikethrough"));
				case "extra" -> {
					if (!value.isJsonArray()) {
						throw new IllegalArgumentException("Text extra must be an array");
					}
					siblings = value.getAsJsonArray();
				}
				case "obfuscated", "clickEvent", "hoverEvent", "insertion", "font",
						"translate", "with", "score", "selector", "keybind", "nbt" ->
					throw new IllegalArgumentException("Unsupported text component or style: " + key);
				default -> throw new IllegalArgumentException("Unknown text property: " + key);
			}
		}
		if (!hasText) throw new IllegalArgumentException("Text object is missing the text property");

		MutableText result = Text.literal(text).setStyle(style);
		if (siblings != null) {
			for (JsonElement sibling : siblings) {
				if (!sibling.isJsonObject()) {
					throw new IllegalArgumentException("Text extra entries must be objects");
				}
				result.append(decodeNode(sibling.getAsJsonObject(), budget, depth + 1));
			}
		}
		return result;
	}

	private static String readString(JsonElement value, String key) {
		if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
			throw new IllegalArgumentException("Text property must be a string: " + key);
		}
		return value.getAsString();
	}

	private static boolean readBoolean(JsonElement value, String key) {
		if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
			throw new IllegalArgumentException("Text property must be a boolean: " + key);
		}
		return value.getAsBoolean();
	}

	private static TextColor readColor(JsonElement value) {
		String color = readString(value, "color");
		return TextColor.parse(color).result()
				.orElseThrow(() -> new IllegalArgumentException("Invalid text color: " + color));
	}

	private static final class NodeBudget {
		private int count;

		private void visit(int depth) {
			if (depth > MAX_DEPTH) {
				throw new IllegalArgumentException("Text tree exceeds maximum depth of " + MAX_DEPTH);
			}
			if (++count > MAX_NODES) {
				throw new IllegalArgumentException("Text tree exceeds maximum node count of " + MAX_NODES);
			}
		}
	}
}
