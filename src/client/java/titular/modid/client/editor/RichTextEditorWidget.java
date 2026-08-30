package titular.modid.client.editor;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import titular.modid.client.ClientText;

/** Reusable, single-line styled literal-text editor. */
public class RichTextEditorWidget extends ClickableWidget {
    private final TextRenderer textRenderer;
    private final StyledTextDocument document;
    private boolean dragging;
    private int scrollX;
    private int dragAnchor;

    public RichTextEditorWidget(TextRenderer textRenderer, int x, int y, int width, int height,
                                StyledTextDocument document) {
        this(textRenderer, x, y, width, height, document, ClientText.text("titular.editor"));
    }

    public RichTextEditorWidget(TextRenderer textRenderer, int x, int y, int width, int height,
                                StyledTextDocument document, Text message) {
        super(x, y, width, height, message == null ? ClientText.text("titular.editor") : message);
        this.textRenderer = textRenderer;
        this.document = document;
        this.active = true;
    }

    public RichTextEditorWidget(int x, int y, int width, int height, StyledTextDocument document) {
        this(MinecraftClient.getInstance().textRenderer, x, y, width, height, document);
    }

    public StyledTextDocument document() { return document; }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(getX(), getY(), getRight(), getBottom(), 0xFF202020);
        context.drawBorder(getX(), getY(), getWidth(), getHeight(), 0xFF707070);
        context.enableScissor(getX() + 2, getY() + 2, getRight() - 2, getBottom() - 2);
        if (document.hasSelection()) {
            int selectionLeft = getX() + 4 + measureUntil(document.selectionStart()) - scrollX;
            int selectionRight = getX() + 4 + measureUntil(document.selectionEnd()) - scrollX;
            context.fill(selectionLeft, getY() + 2, selectionRight,
                    getBottom() - 2, 0x804080C0);
        }
        Text value = document.toText();
        if (document.text().isEmpty() && !isFocused()) {
            context.drawTextWithShadow(textRenderer, getMessage(), getX() + 4 - scrollX,
                    getY() + (getHeight() - textRenderer.fontHeight) / 2, 0xFF8D96A5);
        } else {
            context.drawTextWithShadow(textRenderer, value, getX() + 4 - scrollX,
                    getY() + (getHeight() - textRenderer.fontHeight) / 2, 0xFFFFFFFF);
        }
        if (isFocused() && !document.hasSelection()) {
            int caretX = getX() + 4 + measureUntil(document.caret()) - scrollX;
            context.fill(caretX, getY() + 2, caretX + 1, getBottom() - 2, 0xFFFFFFFF);
        }
        context.disableScissor();
        ensureCaretVisible();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isMouseOver(mouseX, mouseY) || button != GLFW.GLFW_MOUSE_BUTTON_1) return false;
        setFocused(true);
        dragging = true;
        document.setCaret(indexAt(mouseX));
        dragAnchor = document.caret();
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (!active || !visible || !dragging || button != GLFW.GLFW_MOUSE_BUTTON_1) return false;
        document.select(dragAnchor, indexAt(mouseX));
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!visible) return false;
        if (button == GLFW.GLFW_MOUSE_BUTTON_1) dragging = false;
        return button == GLFW.GLFW_MOUSE_BUTTON_1;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isFocused() || !active) return false;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE -> document.backspace();
            case GLFW.GLFW_KEY_DELETE -> document.delete();
            case GLFW.GLFW_KEY_LEFT -> document.moveCaret(-1, shift);
            case GLFW.GLFW_KEY_RIGHT -> document.moveCaret(1, shift);
            case GLFW.GLFW_KEY_HOME -> document.moveHome(shift);
            case GLFW.GLFW_KEY_END -> document.moveEnd(shift);
            default -> { return false; }
        }
        ensureCaretVisible();
        return true;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!isFocused() || !active || Character.isISOControl(chr)) return false;
        document.insert(String.valueOf(chr));
        ensureCaretVisible();
        return true;
    }

    private int indexAt(double mouseX) {
        float target = (float) (mouseX - getX() - 4 + scrollX);
        if (target <= 0) return 0;
        String value = document.text();
        float width = 0;
        for (int index = 0; index < value.length();) {
            int next = Character.charCount(value.codePointAt(index)) + index;
            Text glyph = Text.literal(value.substring(index, next)).setStyle(document.styleAt(index));
            float glyphWidth = textRenderer.getWidth(glyph);
            if (target < width + glyphWidth / 2f) return index;
            width += glyphWidth;
            index = next;
        }
        return value.length();
    }

    private int measureUntil(int end) {
        String value = document.text();
        int safeEnd = Math.max(0, Math.min(end, value.length()));
        int width = 0;
        for (int index = 0; index < safeEnd;) {
            int next = Math.min(safeEnd, index + Character.charCount(value.codePointAt(index)));
            width += textRenderer.getWidth(Text.literal(value.substring(index, next))
                    .setStyle(document.styleAt(index)));
            index = next;
        }
        return width;
    }

    private void ensureCaretVisible() {
        int caretWidth = measureUntil(document.caret());
        int inner = Math.max(1, getWidth() - 8);
        if (caretWidth - scrollX > inner) scrollX = caretWidth - inner;
        if (caretWidth - scrollX < 0) scrollX = caretWidth;
        scrollX = Math.max(0, scrollX);
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        builder.put(net.minecraft.client.gui.screen.narration.NarrationPart.TITLE, getMessage());
    }
}
