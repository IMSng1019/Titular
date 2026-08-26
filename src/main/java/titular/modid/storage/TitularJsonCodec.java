package titular.modid.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import titular.modid.codec.TextJsonCodec;
import titular.modid.model.DisplayMode;
import titular.modid.model.GroupDefinition;
import titular.modid.model.PlayerTitleState;
import titular.modid.model.TitularSettings;
import titular.modid.model.TitleDefinition;
import titular.modid.model.TitularLimits;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class TitularJsonCodec {
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

	String encodeTitles(Map<String, TitleDefinition> titles) {
		if (titles.size() > TitularLimits.MAX_DEFINITIONS) throw new IllegalArgumentException("Too many titles");
		JsonObject root = new JsonObject();
		for (Map.Entry<String, TitleDefinition> entry : titles.entrySet()) {
			TitleDefinition title = entry.getValue();
			if (title == null || !entry.getKey().equals(title.id())) throw new IllegalArgumentException("Title map key mismatch");
			validateId(title.id(), "title id");
			JsonObject value = new JsonObject();
			value.add("prefix", JsonParser.parseString(TextJsonCodec.encode(title.prefix())));
			value.add("suffix", JsonParser.parseString(TextJsonCodec.encode(title.suffix())));
			root.add(title.id(), value);
		}
		return GSON.toJson(root);
	}

	Map<String, TitleDefinition> decodeTitles(String json) {
		JsonObject root = object(json, "titles");
		Map<String, TitleDefinition> result = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
			if (result.size() >= TitularLimits.MAX_DEFINITIONS) throw new IllegalArgumentException("Too many titles");
			if (entry.getKey().isBlank() || entry.getKey().length() > TitularLimits.MAX_STRING_LENGTH) throw new IllegalArgumentException("Invalid title id");
			JsonObject value = object(entry.getValue(), "title " + entry.getKey());
			result.put(entry.getKey(), new TitleDefinition(entry.getKey(),
				textOrNull(value, "prefix"), textOrNull(value, "suffix")));
		}
		return result;
	}

	String encodeGroups(Map<String, GroupDefinition> groups) {
		if (groups.size() > TitularLimits.MAX_DEFINITIONS) throw new IllegalArgumentException("Too many groups");
		JsonObject root = new JsonObject();
		for (Map.Entry<String, GroupDefinition> entry : groups.entrySet()) {
			GroupDefinition group = entry.getValue();
			if (group == null || !entry.getKey().equals(group.id())) throw new IllegalArgumentException("Group map key mismatch");
			validateId(group.id(), "group id");
			if (group.parent() != null) validateId(group.parent(), "parent id");
			JsonObject value = new JsonObject();
			if (group.parent() != null) value.addProperty("parent", group.parent());
			value.add("titles", strings(group.titleIds()));
			root.add(group.id(), value);
		}
		return GSON.toJson(root);
	}

	Map<String, GroupDefinition> decodeGroups(String json) {
		JsonObject root = object(json, "groups");
		Map<String, GroupDefinition> result = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
			if (result.size() >= TitularLimits.MAX_DEFINITIONS) throw new IllegalArgumentException("Too many groups");
			if (entry.getKey().isBlank() || entry.getKey().length() > TitularLimits.MAX_STRING_LENGTH) throw new IllegalArgumentException("Invalid group id");
			JsonObject value = object(entry.getValue(), "group " + entry.getKey());
			result.put(entry.getKey(), new GroupDefinition(entry.getKey(), optionalString(value, "parent"), stringList(value, "titles")));
		}
		return result;
	}

	String encodePlayers(Map<UUID, PlayerTitleState> players) {
		if (players.size() > TitularLimits.MAX_DEFINITIONS) throw new IllegalArgumentException("Too many players");
		JsonObject root = new JsonObject();
		for (Map.Entry<UUID, PlayerTitleState> entry : players.entrySet()) {
			PlayerTitleState player = entry.getValue();
			if (player == null || !entry.getKey().equals(player.playerId())) throw new IllegalArgumentException("Player map key mismatch");
			JsonObject value = new JsonObject();
			addOptional(value, "primaryGroup", player.primaryGroup());
			value.add("extraGroups", strings(player.extraGroups()));
			value.add("extraTitles", strings(player.extraTitles()));
			addOptional(value, "activeTitle", player.activeTitle());
			value.add("luckPermsGroups", strings(player.luckPermsGroups()));
			root.add(player.playerId().toString(), value);
		}
		return GSON.toJson(root);
	}

	Map<UUID, PlayerTitleState> decodePlayers(String json) {
		JsonObject root = object(json, "players");
		Map<UUID, PlayerTitleState> result = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
			if (result.size() >= TitularLimits.MAX_DEFINITIONS) throw new IllegalArgumentException("Too many players");
			UUID playerId;
			try {
				playerId = UUID.fromString(entry.getKey());
			} catch (IllegalArgumentException exception) {
				throw new IllegalArgumentException("Invalid player UUID: " + entry.getKey(), exception);
			}
			JsonObject value = object(entry.getValue(), "player " + entry.getKey());
			result.put(playerId, new PlayerTitleState(playerId, optionalString(value, "primaryGroup"),
				stringList(value, "extraGroups"), stringList(value, "extraTitles"),
				optionalString(value, "activeTitle"), stringList(value, "luckPermsGroups")));
		}
		return result;
	}

	String encodeSettings(TitularSettings settings, long revision) {
		JsonObject root = new JsonObject();
		root.addProperty("displayMode", settings.displayMode().name());
		return GSON.toJson(root);
	}

	SettingsDocument decodeSettings(String json) {
		JsonObject root = object(json, "settings");
		DisplayMode mode = DisplayMode.PREFIX;
		if (root.has("displayMode")) {
			String value = requiredString(root.get("displayMode"), "displayMode");
			try {
				mode = DisplayMode.valueOf(value);
			} catch (IllegalArgumentException exception) {
				throw new IllegalArgumentException("Invalid display mode: " + value, exception);
			}
		}
		// Revisions are process-local optimistic-concurrency tokens and are
		// intentionally reset after a server restart.
		return new SettingsDocument(new TitularSettings(mode), 0L);
	}

	private static JsonObject object(String json, String description) {
		try {
			JsonElement parsed = JsonParser.parseString(json);
			if (!parsed.isJsonObject()) throw new IllegalArgumentException(description + " JSON must be an object");
			return parsed.getAsJsonObject();
		} catch (RuntimeException exception) {
			throw new IllegalArgumentException("Invalid " + description + " JSON", exception);
		}
	}

	private static JsonObject object(JsonElement element, String description) {
		if (element == null || !element.isJsonObject()) throw new IllegalArgumentException(description + " must be an object");
		return element.getAsJsonObject();
	}

	private static JsonArray strings(List<String> values) {
		if (values.size() > TitularLimits.MAX_REFERENCE_LIST) throw new IllegalArgumentException("Too many references");
		JsonArray result = new JsonArray();
		for (String value : values) {
			validateId(value, "reference");
			result.add(value);
		}
		return result;
	}

	private static List<String> stringList(JsonObject object, String key) {
		if (!object.has(key)) return List.of();
		JsonElement element = object.get(key);
		if (!element.isJsonArray()) throw new IllegalArgumentException(key + " must be an array");
		if (element.getAsJsonArray().size() > TitularLimits.MAX_REFERENCE_LIST) throw new IllegalArgumentException("Too many " + key);
		List<String> result = new ArrayList<>();
		for (JsonElement value : element.getAsJsonArray()) {
			String item = requiredString(value, key);
			if (item.isBlank()) throw new IllegalArgumentException(key + " contains a blank reference");
			result.add(item);
		}
		return result;
	}

	private static String optionalString(JsonObject object, String key) {
		if (!object.has(key) || object.get(key).isJsonNull()) return null;
		String value = requiredString(object.get(key), key);
		if (value.isBlank()) throw new IllegalArgumentException(key + " cannot be blank");
		return value;
	}

	private static net.minecraft.text.Text textOrNull(JsonObject object, String key) {
		if (!object.has(key) || object.get(key).isJsonNull()) return null;
		return TextJsonCodec.decode(object.get(key).toString());
	}

	private static String requiredString(JsonElement element, String key) {
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
			throw new IllegalArgumentException(key + " must be a string");
		}
		String value = element.getAsString();
		if (value.length() > TitularLimits.MAX_STRING_LENGTH) throw new IllegalArgumentException(key + " is too long");
		return value;
	}

	private static void addOptional(JsonObject object, String key, String value) {
		if (value != null) {
			validateId(value, key);
			object.addProperty(key, value);
		}
	}

	private static void validateId(String value, String description) {
		if (value == null || value.isBlank() || value.length() > TitularLimits.MAX_STRING_LENGTH) {
			throw new IllegalArgumentException("Invalid " + description);
		}
	}

	record SettingsDocument(TitularSettings settings, long revision) {}
}
