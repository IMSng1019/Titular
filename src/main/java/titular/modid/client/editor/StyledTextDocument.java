package titular.modid.client.editor;

import net.minecraft.text.PlainTextContent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Mutable literal-text document used by the title editor.
 *
 * <p>Positions are UTF-16 offsets, matching Minecraft's string and text APIs.
 * Positions inside a surrogate pair are normalized to the beginning of that
 * pair, and deletion/movement always consumes a complete code point.</p>
 */
public final class StyledTextDocument {
    private final StringBuilder text;
    private final ArrayList<Style> styles;
    private int caret;
    private int anchor;
    private Style insertionStyle = Style.EMPTY;

    public record StyledRun(String text, Style style) {
        public StyledRun {
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(style, "style");
        }
    }

    public StyledTextDocument() {
        this("");
    }

    public StyledTextDocument(String text) {
        this(text, Style.EMPTY);
    }

    public StyledTextDocument(String text, Style style) {
        this.text = new StringBuilder(Objects.requireNonNull(text, "text"));
        Style safeStyle = Objects.requireNonNull(style, "style");
        this.styles = new ArrayList<>(text.length());
        for (int i = 0; i < text.length(); i++) this.styles.add(safeStyle);
        this.caret = this.anchor = text.length();
        this.insertionStyle = safeStyle;
    }

    public static StyledTextDocument fromText(Text source) {
        Objects.requireNonNull(source, "source");
        StyledTextDocument result = new StyledTextDocument();
        appendLiteral(source, Style.EMPTY, result.text, result.styles);
        result.caret = result.anchor = result.text.length();
        result.insertionStyle = result.styles.isEmpty() ? Style.EMPTY : result.styles.get(result.styles.size() - 1);
        return result;
    }

    private static void appendLiteral(Text source, Style parentStyle, StringBuilder text, ArrayList<Style> styles) {
        if (!(source.getContent() instanceof PlainTextContent literal)) {
            throw new IllegalArgumentException("Only literal text components are supported");
        }
        String value = literal.string();
        Style style = source.getStyle().withParent(parentStyle);
        if (style == null) style = Style.EMPTY;
        text.append(value);
        for (int i = 0; i < value.length(); i++) styles.add(style);
        for (Text sibling : source.getSiblings()) {
            if (sibling == null) throw new IllegalArgumentException("Text contains a null sibling");
            appendLiteral(sibling, style, text, styles);
        }
    }

    public String text() { return text.toString(); }
    public int length() { return text.length(); }
    public int caret() { return caret; }
    public int selectionStart() { return Math.min(anchor, caret); }
    public int selectionEnd() { return Math.max(anchor, caret); }
    public boolean hasSelection() { return selectionStart() < selectionEnd(); }

    public Style styleAt(int index) {
        if (index >= text.length() || styles.isEmpty()) return insertionStyle;
        int safe = Math.max(0, Math.min(index, styles.size() - 1));
        return styles.get(safe);
    }

    public List<StyledRun> runs() {
        ArrayList<StyledRun> result = new ArrayList<>();
        if (text.isEmpty()) return List.of();
        int start = 0;
        Style current = styles.get(0);
        for (int i = 1; i < text.length(); i++) {
            Style next = styles.get(i);
            if (!current.equals(next)) {
                result.add(new StyledRun(text.substring(start, i), current));
                start = i;
                current = next;
            }
        }
        result.add(new StyledRun(text.substring(start), current));
        return List.copyOf(result);
    }

    public MutableText toText() {
        if (text.isEmpty()) return Text.empty();
        MutableText result = null;
        for (StyledRun run : runs()) {
            MutableText part = Text.literal(run.text()).setStyle(run.style());
            if (result == null) result = part;
            else result.append(part);
        }
        return result == null ? Text.empty() : result;
    }

    public void setCaret(int position) {
        caret = normalize(position);
        anchor = caret;
        updateInsertionStyleFromCaret();
    }

    public void moveCaret(int delta, boolean extendSelection) {
        if (!extendSelection && hasSelection()) {
            caret = delta < 0 ? selectionStart() : selectionEnd();
            anchor = caret;
            updateInsertionStyleFromCaret();
            return;
        }
        int destination = caret;
        if (delta < 0) while (delta++ < 0) destination = previousBoundary(destination);
        else while (delta-- > 0) destination = nextBoundary(destination);
        caret = normalize(destination);
        if (!extendSelection) anchor = caret;
        updateInsertionStyleFromCaret();
    }

    public void moveHome(boolean extendSelection) { caret = 0; if (!extendSelection) anchor = caret; updateInsertionStyleFromCaret(); }
    public void moveEnd(boolean extendSelection) { caret = text.length(); if (!extendSelection) anchor = caret; updateInsertionStyleFromCaret(); }

    public void select(int start, int end) {
        anchor = normalize(start);
        caret = normalize(end);
        updateInsertionStyleFromCaret();
    }

    public void replaceSelection(String replacement) {
        Objects.requireNonNull(replacement, "replacement");
        int start = selectionStart();
        int end = selectionEnd();
        Style style = hasSelection() ? styles.get(start) : insertionStyle;
        text.replace(start, end, replacement);
        styles.subList(start, end).clear();
        for (int i = 0; i < replacement.length(); i++) styles.add(start + i, style);
        insertionStyle = style;
        caret = normalize(start + replacement.length());
        anchor = caret;
    }

    public void insert(String value) { replaceSelection(value); }

    public void backspace() {
        if (hasSelection()) { replaceSelection(""); return; }
        if (caret == 0) return;
        int previous = previousBoundary(caret);
        select(previous, caret);
        replaceSelection("");
    }

    public void delete() {
        if (hasSelection()) { replaceSelection(""); return; }
        if (caret >= text.length()) return;
        int next = nextBoundary(caret);
        select(caret, next);
        replaceSelection("");
    }

    public Style insertionStyle() { return insertionStyle; }

    private void updateInsertionStyleFromCaret() {
        if (caret > 0 && caret <= styles.size()) insertionStyle = styles.get(caret - 1);
        else if (caret < styles.size()) insertionStyle = styles.get(caret);
    }

    public void applyColor(Formatting formatting) {
        Objects.requireNonNull(formatting, "formatting");
        if (!formatting.isColor()) throw new IllegalArgumentException("Formatting is not a color");
        apply(style -> style.withColor(formatting));
    }

    public void applyColor(TextColor color) { apply(style -> style.withColor(color)); }
    public void toggleBold() { apply(style -> style.withBold(!style.isBold())); }
    public void toggleItalic() { apply(style -> style.withItalic(!style.isItalic())); }
    public void toggleUnderline() { apply(style -> style.withUnderline(!style.isUnderlined())); }
    public void toggleStrikethrough() { apply(style -> style.withStrikethrough(!style.isStrikethrough())); }
    public void resetStyle() { apply(style -> Style.EMPTY); }

    private void apply(UnaryOperator<Style> operation) {
        int start = selectionStart();
        int end = selectionEnd();
        if (start == end) {
            insertionStyle = operation.apply(insertionStyle);
            return;
        }
        for (int i = start; i < end; i++) styles.set(i, operation.apply(styles.get(i)));
        insertionStyle = styles.get(Math.min(end - 1, styles.size() - 1));
    }

    private int normalize(int position) {
        int safe = Math.max(0, Math.min(position, text.length()));
        if (safe > 0 && safe < text.length()
                && Character.isHighSurrogate(text.charAt(safe - 1))
                && Character.isLowSurrogate(text.charAt(safe))) return safe - 1;
        return safe;
    }

    private int previousBoundary(int position) {
        int safe = normalize(position);
        if (safe > 0 && Character.isLowSurrogate(text.charAt(safe - 1))) return safe - 2;
        return safe - 1;
    }

    private int nextBoundary(int position) {
        int safe = normalize(position);
        if (safe < text.length() - 1 && Character.isHighSurrogate(text.charAt(safe))
                && Character.isLowSurrogate(text.charAt(safe + 1))) return safe + 2;
        return safe + 1;
    }
}
