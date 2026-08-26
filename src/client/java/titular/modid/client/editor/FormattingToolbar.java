package titular.modid.client.editor;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Compact toolbar that applies the editor's supported visual styles. */
public final class FormattingToolbar extends ClickableWidget {
    private final List<ButtonWidget> buttons = new ArrayList<>();

    public FormattingToolbar(int x, int y, StyledTextDocument document) {
        super(x, y, 18 * 21, 20, Text.translatable("titular.editor.toolbar"));
        int offset = 0;
        for (Formatting color : Formatting.values()) {
            if (!color.isColor()) continue;
            addColorButton(document, color, offset++);
        }
        addButton("B", "titular.editor.bold", offset++, document::toggleBold);
        addButton("I", "titular.editor.italic", offset++, document::toggleItalic);
        addButton("U", "titular.editor.underline", offset++, document::toggleUnderline);
        addButton("S", "titular.editor.strikethrough", offset++, document::toggleStrikethrough);
        addButton("R", "titular.editor.reset", offset, document::resetStyle);
        setWidth(Math.max(18, offset * 18 + 18));
    }

    private void addColorButton(StyledTextDocument document, Formatting color, int offset) {
        Text label = Text.literal("■").formatted(color);
        ButtonWidget button = ButtonWidget.builder(label, ignored -> document.applyColor(color))
                .dimensions(getX() + offset * 18, getY(), 18, 20)
                .tooltip(Tooltip.of(Text.translatable("titular.editor.color." + color.getName())))
                .build();
        buttons.add(button);
    }

    private void addButton(String label, String tooltipKey, int offset, Runnable action) {
        ButtonWidget button = ButtonWidget.builder(Text.literal(label), ignored -> action.run())
                .dimensions(getX() + offset * 18, getY(), 18, 20)
                .tooltip(Tooltip.of(Text.translatable(tooltipKey)))
                .build();
        buttons.add(button);
    }

    public List<ButtonWidget> buttons() { return List.copyOf(buttons); }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!visible) return;
        syncChildPositions();
        for (ButtonWidget button : buttons) {
            button.active = active;
            button.visible = visible;
            button.render(context, mouseX, mouseY, delta);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!active || !visible) return false;
        syncChildPositions();
        for (ButtonWidget child : buttons) if (child.mouseClicked(mouseX, mouseY, button)) return true;
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!visible) return false;
        boolean handled = false;
        for (ButtonWidget child : buttons) handled |= child.mouseReleased(mouseX, mouseY, button);
        return handled;
    }

    @Override
    public void setX(int x) {
        super.setX(x);
        syncChildPositions();
    }

    @Override
    public void setY(int y) {
        super.setY(y);
        syncChildPositions();
    }

    private void syncChildPositions() {
        for (int i = 0; i < buttons.size(); i++) {
            buttons.get(i).setPosition(getX() + i * 18, getY());
        }
    }

    @Override
    public void forEachChild(Consumer<ClickableWidget> consumer) {
        if (visible) buttons.forEach(consumer);
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        builder.put(net.minecraft.client.gui.screen.narration.NarrationPart.TITLE, getMessage());
    }
}
