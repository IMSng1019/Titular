package titular.modid.model;

import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelDefaultsTest {
	@Test
	void appliesEmptyDefaultsAndPermitsNullableReferences() {
		TitleDefinition title = new TitleDefinition("trailblazer", null, null);
		GroupDefinition group = new GroupDefinition("adventurer", null, null);
		PlayerTitleState player = new PlayerTitleState(UUID.randomUUID(), null, null, null, null, null);
		TitularSettings settings = new TitularSettings(null);
		TitularData data = new TitularData(null, null, null, null, 7L);

		assertEquals("", title.prefix().getString());
		assertEquals("", title.suffix().getString());
		assertNull(group.parent());
		assertEquals(List.of(), group.titleIds());
		assertNull(player.primaryGroup());
		assertNull(player.activeTitle());
		assertEquals(List.of(), player.extraGroups());
		assertEquals(List.of(), player.extraTitles());
		assertEquals(List.of(), player.luckPermsGroups());
		assertEquals(DisplayMode.PREFIX, settings.displayMode());
		assertEquals(7L, data.revision());
		assertTrue(data.titles().isEmpty());
		assertTrue(data.groups().isEmpty());
		assertTrue(data.players().isEmpty());
	}

	@Test
	void defensivelyCopiesCollectionsWhileKeepingTheirOrder() {
		List<String> titleIds = new ArrayList<>(List.of("first", "second"));
		List<String> extraGroups = new ArrayList<>(List.of("staff"));
		List<String> extraTitles = new ArrayList<>(List.of("veteran"));
		List<String> luckPermsGroups = new ArrayList<>(List.of("vip"));
		Map<String, TitleDefinition> titles = new LinkedHashMap<>();
		TitleDefinition veteran = new TitleDefinition("veteran", Text.literal("[V]"), Text.empty());
		titles.put("veteran", veteran);
		Map<String, GroupDefinition> groups = new LinkedHashMap<>();
		groups.put("staff", new GroupDefinition("staff", null, titleIds));
		Map<UUID, PlayerTitleState> players = new LinkedHashMap<>();
		UUID playerId = UUID.randomUUID();
		players.put(playerId, new PlayerTitleState(playerId, "staff", extraGroups, extraTitles, "veteran", luckPermsGroups));

		GroupDefinition group = groups.get("staff");
		PlayerTitleState player = players.get(playerId);
		TitularData data = new TitularData(titles, groups, players, new TitularSettings(DisplayMode.BOTH), 3L);

		titleIds.add("later");
		extraGroups.add("later");
		extraTitles.add("later");
		luckPermsGroups.add("later");
		titles.clear();
		groups.clear();
		players.clear();

		assertEquals(List.of("first", "second"), group.titleIds());
		assertEquals(List.of("staff"), player.extraGroups());
		assertEquals(List.of("veteran"), player.extraTitles());
		assertEquals(List.of("vip"), player.luckPermsGroups());
		assertEquals(List.of("veteran"), List.copyOf(data.titles().keySet()));
		assertEquals(List.of("staff"), List.copyOf(data.groups().keySet()));
		assertEquals(List.of(playerId), List.copyOf(data.players().keySet()));
		assertThrows(UnsupportedOperationException.class, () -> data.titles().put("other", veteran));
	}
}
