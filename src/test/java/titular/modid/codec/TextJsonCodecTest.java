package titular.modid.codec;

import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.text.Style;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TextJsonCodecTest {
	@Test
	void roundTripsStyledSiblingTextDeterministically() {
		Text source = Text.literal("red")
			.formatted(Formatting.RED, Formatting.BOLD)
			.append(Text.literal("blue").formatted(Formatting.BLUE, Formatting.ITALIC));

		String encoded = TextJsonCodec.encode(source);
		Text decoded = TextJsonCodec.decode(encoded);

		assertEquals(encoded, TextJsonCodec.encode(decoded));
		assertEquals(encoded, TextJsonCodec.encode(source));
	}

	@Test
	void rejectsMalformedOrNonObjectJson() {
		assertThrows(IllegalArgumentException.class, () -> TextJsonCodec.decode("not json"));
		assertThrows(IllegalArgumentException.class, () -> TextJsonCodec.decode("\"plain text\""));
		assertThrows(IllegalArgumentException.class, () -> TextJsonCodec.decode("{}"));
	}

	@Test
	void rejectsUnsupportedComponentsAndStyles() {
		assertThrows(IllegalArgumentException.class,
			() -> TextJsonCodec.decode("{\"translate\":\"chat.type.text\"}"));
		assertThrows(IllegalArgumentException.class,
			() -> TextJsonCodec.decode("{\"text\":\"secret\",\"obfuscated\":true}"));
	}

	@Test
	void rejectsExcessivelyDeepTextTrees() {
		StringBuilder json = new StringBuilder("{\"text\":\"x\",\"extra\":[");
		for (int i = 0; i < 80; i++) json.append("{\"text\":\"x\",\"extra\":[");
		json.append("{\"text\":\"leaf\"}");
		for (int i = 0; i < 81; i++) json.append("]}");
		assertThrows(IllegalArgumentException.class, () -> TextJsonCodec.decode(json.toString()));
	}

	@Test
	void preservesInheritedParentStyleAcrossSiblings() {
		Text source = Text.literal("parent").setStyle(Style.EMPTY.withColor(Formatting.RED))
			.append(Text.literal("child"));
		Text decoded = TextJsonCodec.decode(TextJsonCodec.encode(source));
		assertEquals(TextJsonCodec.encode(source), TextJsonCodec.encode(decoded));
		assertEquals("red", decoded.getSiblings().get(0).getStyle().getColor().getName());
	}

	@Test
	void rejectsTextTreesWithTooManyNodes() {
		StringBuilder json = new StringBuilder("{\"text\":\"root\",\"extra\":[");
		for (int i = 0; i < 2048; i++) {
			if (i > 0) json.append(',');
			json.append("{\"text\":\"x\"}");
		}
		json.append("]}");
		assertThrows(IllegalArgumentException.class, () -> TextJsonCodec.decode(json.toString()));
	}
}
