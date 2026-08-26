package titular.modid.format;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.junit.jupiter.api.Test;
import titular.modid.codec.TextJsonCodec;
import titular.modid.model.DisplayMode;
import titular.modid.model.TitleDefinition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class TitularFormatterTest {
	@Test
	void formatsBothWithIndependentStyledPrefixNameAndSuffixSiblings() {
		Text original = Text.literal("Ada")
			.formatted(Formatting.WHITE)
			.append(Text.literal("Lovelace").formatted(Formatting.AQUA, Formatting.ITALIC));
		Text prefix = Text.literal("[").formatted(Formatting.GOLD, Formatting.BOLD)
			.append(Text.literal("VIP").formatted(Formatting.RED, Formatting.BOLD))
			.append(Text.literal("] ").formatted(Formatting.GOLD));
		Text suffix = Text.literal(" ").formatted(Formatting.GRAY)
			.append(Text.literal("*").formatted(Formatting.LIGHT_PURPLE, Formatting.UNDERLINE));
		TitleDefinition title = new TitleDefinition("vip", prefix, suffix);
		String originalJson = TextJsonCodec.encode(original);
		String prefixJson = TextJsonCodec.encode(prefix);
		String suffixJson = TextJsonCodec.encode(suffix);

		Text formatted = TitularFormatter.format(original, title, DisplayMode.BOTH);

		assertEquals(originalJson, TextJsonCodec.encode(original));
		assertEquals("{\"text\":\"\",\"extra\":["
			+ "{\"text\":\"[\",\"color\":\"gold\",\"bold\":true,\"extra\":["
			+ "{\"text\":\"VIP\",\"color\":\"red\",\"bold\":true},"
			+ "{\"text\":\"] \",\"color\":\"gold\",\"bold\":true}]},"
			+ "{\"text\":\"Ada\",\"color\":\"white\",\"extra\":["
			+ "{\"text\":\"Lovelace\",\"color\":\"aqua\",\"italic\":true}]},"
			+ "{\"text\":\" \",\"color\":\"gray\",\"extra\":["
			+ "{\"text\":\"*\",\"color\":\"light_purple\",\"underlined\":true}]}]}"
			, TextJsonCodec.encode(formatted));
		assertEquals(prefixJson, TextJsonCodec.encode(title.prefix()));
		assertEquals(suffixJson, TextJsonCodec.encode(title.suffix()));
		assertNotSame(original, formatted);
	}

	@Test
	void supportsEachDisplayModeAndNullModeDefaultsToPrefix() {
		Text original = Text.literal("Nora");
		TitleDefinition title = new TitleDefinition("honor", Text.literal("[H]"), Text.literal("!").formatted(Formatting.RED));

		assertEquals("{\"text\":\"\",\"extra\":[{\"text\":\"[H]\"},{\"text\":\"Nora\"}]}"
			, TextJsonCodec.encode(TitularFormatter.format(original, title, DisplayMode.PREFIX)));
		assertEquals("{\"text\":\"\",\"extra\":[{\"text\":\"Nora\"},{\"text\":\"!\",\"color\":\"red\"}]}"
			, TextJsonCodec.encode(TitularFormatter.format(original, title, DisplayMode.SUFFIX)));
		assertEquals("{\"text\":\"\",\"extra\":[{\"text\":\"[H]\"},{\"text\":\"Nora\"},{\"text\":\"!\",\"color\":\"red\"}]}"
			, TextJsonCodec.encode(TitularFormatter.format(original, title, DisplayMode.BOTH)));
		assertEquals("{\"text\":\"\",\"extra\":[{\"text\":\"[H]\"},{\"text\":\"Nora\"}]}"
			, TextJsonCodec.encode(TitularFormatter.format(original, title, null)));
	}

	@Test
	void nullOrEmptyTitleReturnsFreshCopyOfOriginalWithoutMutation() {
		Text original = Text.literal("Player").formatted(Formatting.DARK_GREEN);
		String originalJson = TextJsonCodec.encode(original);

		Text nullTitle = TitularFormatter.format(original, null, DisplayMode.BOTH);
		Text emptyTitle = TitularFormatter.format(original, new TitleDefinition("empty"), DisplayMode.BOTH);

		assertEquals("{\"text\":\"\",\"extra\":[{\"text\":\"Player\",\"color\":\"dark_green\"}]}"
			, TextJsonCodec.encode(nullTitle));
		assertEquals(TextJsonCodec.encode(nullTitle), TextJsonCodec.encode(emptyTitle));
		assertEquals(originalJson, TextJsonCodec.encode(original));
		assertNotSame(original, nullTitle);
	}
}
