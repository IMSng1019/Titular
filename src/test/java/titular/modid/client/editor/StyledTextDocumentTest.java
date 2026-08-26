package titular.modid.client.editor;

import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StyledTextDocumentTest {
    @Test
    void normalizesAndClampsHalfOpenSelections() {
        StyledTextDocument document = new StyledTextDocument("hello");

        document.select(4, 1);
        assertEquals(1, document.selectionStart());
        assertEquals(4, document.selectionEnd());
        document.select(-10, 99);
        assertEquals(0, document.selectionStart());
        assertEquals(5, document.selectionEnd());
    }

    @Test
    void replacesSelectionAndSupportsBackspaceAndDelete() {
        StyledTextDocument document = new StyledTextDocument("hello");
        document.select(1, 4);
        document.replaceSelection("i");
        assertEquals("hio", document.text());
        document.setCaret(1);
        document.backspace();
        assertEquals("io", document.text());
        document.delete();
        assertEquals("o", document.text());
    }

    @Test
    void togglesEverySupportedStyleAndResetsSelection() {
        StyledTextDocument document = new StyledTextDocument("abc");
        document.select(0, 3);
        document.applyColor(Formatting.RED);
        document.toggleBold();
        document.toggleItalic();
        document.toggleUnderline();
        document.toggleStrikethrough();
        Style styled = document.styleAt(1);
        assertEquals("red", styled.getColor().getName());
        assertTrue(styled.isBold());
        assertTrue(styled.isItalic());
        assertTrue(styled.isUnderlined());
        assertTrue(styled.isStrikethrough());
        document.resetStyle();
        assertEquals(Style.EMPTY, document.styleAt(1));
    }

    @Test
    void emptySelectionStyleIsUsedForFutureTyping() {
        StyledTextDocument document = new StyledTextDocument("ab");
        document.setCaret(1);
        document.applyColor(Formatting.BLUE);
        document.insert("x");
        assertEquals("axb", document.text());
        assertEquals("blue", document.styleAt(1).getColor().getName());
    }

    @Test
    void keepsUnicodeSurrogatePairsIntactWhenMovingAndDeleting() {
        StyledTextDocument document = new StyledTextDocument("A😀B");
        assertEquals(4, document.length()); // indices are UTF-16 offsets
        document.setCaret(2); // between the pair's UTF-16 code units normalizes to its beginning
        assertEquals(1, document.caret());
        document.delete();
        assertEquals("AB", document.text());
    }

    @Test
    void mergesAdjacentEqualRunsAndRoundTripsLiteralTextTree() {
        Text source = Text.literal("ab").setStyle(Style.EMPTY.withColor(Formatting.RED))
                .append(Text.literal("cd").setStyle(Style.EMPTY.withColor(Formatting.RED).withBold(true)))
                .append(Text.literal("ef").setStyle(Style.EMPTY.withColor(Formatting.RED).withBold(true)));
        StyledTextDocument document = StyledTextDocument.fromText(source);

        assertEquals(2, document.runs().size());
        Text roundTrip = document.toText();
        assertEquals("abcdef", roundTrip.getString());
        assertEquals(2, StyledTextDocument.fromText(roundTrip).runs().size());
        assertEquals(document.runs(), StyledTextDocument.fromText(roundTrip).runs());
    }

    @Test
    void inheritsParentStyleAndCollapsesSelectionOnArrowMovement() {
        Text source = Text.literal("x").setStyle(Style.EMPTY.withColor(Formatting.RED))
                .append(Text.literal("y"));
        StyledTextDocument document = StyledTextDocument.fromText(source);
        assertEquals("red", document.styleAt(1).getColor().getName());

        document = new StyledTextDocument("abcd");
        document.select(1, 4);
        document.moveCaret(-1, false);
        assertEquals(1, document.caret());
        document.select(1, 4);
        document.moveCaret(1, false);
        assertEquals(4, document.caret());
    }
}
