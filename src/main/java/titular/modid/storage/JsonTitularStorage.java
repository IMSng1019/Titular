package titular.modid.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import titular.modid.model.GroupDefinition;
import titular.modid.model.PlayerTitleState;
import titular.modid.model.TitularData;
import titular.modid.model.TitularSettings;
import titular.modid.model.TitleDefinition;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

public final class JsonTitularStorage implements TitularStorage {
	private static final Logger LOGGER = LoggerFactory.getLogger("titular-storage");
	private static final String TITLES_FILE = "titles.json";
	private static final String GROUPS_FILE = "groups.json";
	private static final String PLAYERS_FILE = "players.json";
	private static final String SETTINGS_FILE = "settings.json";

	private final Path directory;
	private final Clock clock;
	private final TitularJsonCodec codec = new TitularJsonCodec();

	public JsonTitularStorage(Path directory) {
		this(directory, Clock.systemUTC());
	}

	JsonTitularStorage(Path directory, Clock clock) {
		this.directory = directory;
		this.clock = clock;
	}

	@Override
	public synchronized TitularData load() {
		ensureDirectory();
		Map<String, TitleDefinition> titles = loadDocument(TITLES_FILE, codec::decodeTitles, Map::of, codec::encodeTitles);
		Map<String, GroupDefinition> groups = loadDocument(GROUPS_FILE, codec::decodeGroups, Map::of, codec::encodeGroups);
		Map<UUID, PlayerTitleState> players = loadDocument(PLAYERS_FILE, codec::decodePlayers, Map::of, codec::encodePlayers);
		TitularJsonCodec.SettingsDocument settings = loadDocument(SETTINGS_FILE, codec::decodeSettings,
			() -> new TitularJsonCodec.SettingsDocument(new TitularSettings(), 0L),
			document -> codec.encodeSettings(document.settings(), document.revision()));
		return new TitularData(titles, groups, players, settings.settings(), settings.revision());
	}

	@Override
	public synchronized void save(TitularData data) {
		ensureDirectory();
		Map<Path, String> documents = new LinkedHashMap<>();
		documents.put(directory.resolve(TITLES_FILE), codec.encodeTitles(data.titles()));
		documents.put(directory.resolve(GROUPS_FILE), codec.encodeGroups(data.groups()));
		documents.put(directory.resolve(PLAYERS_FILE), codec.encodePlayers(data.players()));
		documents.put(directory.resolve(SETTINGS_FILE), codec.encodeSettings(data.settings(), data.revision()));
		writeSnapshot(documents);
	}

	private <T> T loadDocument(String fileName, Function<String, T> decoder, Supplier<T> defaultValue, Function<T, String> encoder) {
		Path path = directory.resolve(fileName);
		if (!Files.exists(path)) {
			T value = defaultValue.get();
			writeAtomically(path, encoder.apply(value));
			return value;
		}
		try {
			return decoder.apply(Files.readString(path, StandardCharsets.UTF_8));
		} catch (IOException | RuntimeException exception) {
			T value = defaultValue.get();
			backupBrokenFile(path, exception);
			writeAtomically(path, encoder.apply(value));
			return value;
		}
	}

	private void ensureDirectory() {
		try {
			Files.createDirectories(directory);
		} catch (IOException exception) {
			throw new StorageException("Could not create Titular config directory " + directory, exception);
		}
	}

	private void backupBrokenFile(Path path, Exception cause) {
		Path backup = nextBackupPath(path, ".broken-");
		try {
			Files.move(path, backup);
			LOGGER.error("Invalid Titular configuration moved from {} to {}", path, backup, cause);
		} catch (IOException exception) {
			throw new StorageException("Could not back up invalid Titular configuration " + path, exception);
		}
	}

	private void writeSnapshot(Map<Path, String> documents) {
		Map<Path, Path> temporaryFiles = new LinkedHashMap<>();
		Map<Path, byte[]> previousContents = new LinkedHashMap<>();
		List<Path> createdTargets = new ArrayList<>();
		try {
			for (Map.Entry<Path, String> entry : documents.entrySet()) {
				Path target = entry.getKey();
				Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
				temporaryFiles.put(target, temporary);
				if (Files.exists(target)) previousContents.put(target, Files.readAllBytes(target));
				Files.writeString(temporary, entry.getValue() + System.lineSeparator(), StandardCharsets.UTF_8);
			}
			for (Map.Entry<Path, Path> entry : temporaryFiles.entrySet()) {
				Path target = entry.getKey();
				try {
					Files.move(entry.getValue(), target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
				} catch (AtomicMoveNotSupportedException exception) {
					Files.move(entry.getValue(), target, StandardCopyOption.REPLACE_EXISTING);
				}
				if (!previousContents.containsKey(target)) createdTargets.add(target);
			}
		} catch (IOException exception) {
			for (Path target : createdTargets) {
				try { Files.deleteIfExists(target); }
				catch (IOException rollbackException) { LOGGER.error("Could not remove partially written Titular file {}", target, rollbackException); }
			}
			for (Map.Entry<Path, byte[]> entry : previousContents.entrySet()) {
				try { Files.write(entry.getKey(), entry.getValue()); }
				catch (IOException rollbackException) { LOGGER.error("Could not restore Titular file {}", entry.getKey(), rollbackException); }
			}
			throw new StorageException("Could not write Titular configuration snapshot", exception);
		} finally {
			for (Path temporary : temporaryFiles.values()) {
				try { Files.deleteIfExists(temporary); }
				catch (IOException exception) { LOGGER.warn("Could not remove temporary Titular file {}", temporary, exception); }
			}
		}
	}

	private void writeAtomically(Path target, String content) {
		writeSnapshot(Map.of(target, content));
	}

	private Path nextBackupPath(Path source, String marker) {
		long timestamp = Instant.now(clock).toEpochMilli();
		Path candidate = source.resolveSibling(source.getFileName() + marker + timestamp);
		int suffix = 0;
		while (Files.exists(candidate)) candidate = source.resolveSibling(source.getFileName() + marker + timestamp + "-" + (++suffix));
		return candidate;
	}
}
