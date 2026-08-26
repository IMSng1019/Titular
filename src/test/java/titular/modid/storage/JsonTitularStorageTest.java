package titular.modid.storage;

import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import titular.modid.codec.TextJsonCodec;
import titular.modid.model.DisplayMode;
import titular.modid.model.GroupDefinition;
import titular.modid.model.PlayerTitleState;
import titular.modid.model.TitularData;
import titular.modid.model.TitularSettings;
import titular.modid.model.TitleDefinition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonTitularStorageTest {

	@TempDir
	Path temporaryDirectory;

	@Test
	void firstLoadCreatesOnlyTheFourEmptyConfigurationFiles() throws IOException {
		Path configDirectory = temporaryDirectory.resolve("titular");

		TitularData loaded = new JsonTitularStorage(configDirectory).load();

		assertTrue(loaded.titles().isEmpty());
		assertTrue(loaded.groups().isEmpty());
		assertTrue(loaded.players().isEmpty());
		assertEquals(DisplayMode.PREFIX, loaded.settings().displayMode());
		assertTrue(Files.exists(configDirectory.resolve("titles.json")));
		assertTrue(Files.exists(configDirectory.resolve("groups.json")));
		assertTrue(Files.exists(configDirectory.resolve("players.json")));
		assertTrue(Files.exists(configDirectory.resolve("settings.json")));
		try (var paths = Files.list(configDirectory)) {
			assertEquals(4L, paths.count());
		}
	}

	@Test
	void savesAndLoadsTheEntireDataSetWithoutLosingRichTextOrOrder() {
		Path configDirectory = temporaryDirectory.resolve("titular");
		UUID playerId = UUID.randomUUID();
		TitleDefinition title = new TitleDefinition(
			"veteran",
			Text.literal("[V] ").formatted(Formatting.GOLD, Formatting.BOLD),
			Text.literal("!").formatted(Formatting.YELLOW)
		);
		Map<String, TitleDefinition> titles = new LinkedHashMap<>();
		titles.put(title.id(), title);
		Map<String, GroupDefinition> groups = new LinkedHashMap<>();
		groups.put("member", new GroupDefinition("member", null, List.of("veteran")));
		Map<UUID, PlayerTitleState> players = new LinkedHashMap<>();
		players.put(playerId, new PlayerTitleState(playerId, "member", List.of("event"), List.of("veteran"), "veteran", List.of("vip")));
		TitularData expected = new TitularData(titles, groups, players, new TitularSettings(DisplayMode.BOTH), 42L);

		JsonTitularStorage storage = new JsonTitularStorage(configDirectory);
		storage.save(expected);
		TitularData actual = storage.load();

		assertEquals(List.of("veteran"), List.copyOf(actual.titles().keySet()));
		assertEquals(List.of("member"), List.copyOf(actual.groups().keySet()));
		assertEquals(List.of(playerId), List.copyOf(actual.players().keySet()));
		assertEquals("member", actual.players().get(playerId).primaryGroup());
		assertEquals(List.of("event"), actual.players().get(playerId).extraGroups());
		assertEquals(List.of("vip"), actual.players().get(playerId).luckPermsGroups());
		assertEquals(DisplayMode.BOTH, actual.settings().displayMode());
		assertEquals(TextJsonCodec.encode(expected.titles().get("veteran").prefix()), TextJsonCodec.encode(actual.titles().get("veteran").prefix()));
		assertEquals(TextJsonCodec.encode(expected.titles().get("veteran").suffix()), TextJsonCodec.encode(actual.titles().get("veteran").suffix()));
	}

	@Test
	void replacementSaveDoesNotLeaveValuesFromThePreviousSnapshot() throws IOException {
		Path configDirectory = temporaryDirectory.resolve("titular");
		JsonTitularStorage storage = new JsonTitularStorage(configDirectory);
		Map<String, TitleDefinition> firstTitles = new LinkedHashMap<>();
		firstTitles.put("old", new TitleDefinition("old", Text.literal("old"), Text.empty()));
		storage.save(new TitularData(firstTitles, null, null, null, 1L));

		storage.save(new TitularData());
		TitularData loaded = storage.load();

		assertTrue(loaded.titles().isEmpty());
		assertTrue(loaded.groups().isEmpty());
		assertTrue(loaded.players().isEmpty());
		assertFalse(Files.readString(configDirectory.resolve("titles.json")).contains("old"));
	}

	@Test
	void firstLoadBacksUpAMalformedDocumentAndRecoversWithDefaults() throws IOException {
		Path configDirectory = temporaryDirectory.resolve("titular");
		Files.createDirectories(configDirectory);
		Files.writeString(configDirectory.resolve("groups.json"), "{broken");

		TitularData loaded = new JsonTitularStorage(configDirectory).load();

		assertTrue(loaded.groups().isEmpty());
		assertTrue(Files.readString(configDirectory.resolve("groups.json")).contains("{}"));
		try (var paths = Files.list(configDirectory)) {
			assertTrue(paths.anyMatch(path -> path.getFileName().toString().startsWith("groups.json.broken-")));
		}
	}

	@Test
	void explicitNullTextFragmentsUseEmptyTextDefaults() throws IOException {
		Path configDirectory = temporaryDirectory.resolve("titular");
		Files.createDirectories(configDirectory);
		Files.writeString(configDirectory.resolve("titles.json"), "{\"empty\":{\"prefix\":null,\"suffix\":null}}");

		TitularData loaded = new JsonTitularStorage(configDirectory).load();

		assertEquals("", loaded.titles().get("empty").prefix().getString());
		assertEquals("", loaded.titles().get("empty").suffix().getString());
	}

	@Test
	void backsUpDocumentsWithOversizedNestedReferences() throws IOException {
		Path configDirectory = temporaryDirectory.resolve("titular");
		Files.createDirectories(configDirectory);
		String oversized = "a".repeat(257);
		Files.writeString(configDirectory.resolve("groups.json"),
			"{\"ok\":{\"parent\":\"" + oversized + "\",\"titles\":[]}}");

		TitularData loaded = new JsonTitularStorage(configDirectory).load();

		assertTrue(loaded.groups().isEmpty());
		try (var paths = Files.list(configDirectory)) {
			assertTrue(paths.anyMatch(path -> path.getFileName().toString().startsWith("groups.json.broken-")));
		}
	}
}
