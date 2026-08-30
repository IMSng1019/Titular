package titular.modid.client.editor;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

/**
 * Read-only live preview for a title editor. The username is kept separate
 * from the editable documents so it can never receive their styles.
 */
public final class TitlePreviewWidget extends ClickableWidget {
    private final TextRenderer textRenderer;
    private final StyledTextDocument prefix;
    private final StyledTextDocument suffix;
    private String username;

    public TitlePreviewWidget(TextRenderer textRenderer, int x, int y, int width, int height,
                              StyledTextDocument prefix, String username, StyledTextDocument suffix) {
        super(x, y, width, height, Text.translatable("titular.editor.preview"));
        this.textRenderer = textRenderer;
        this.prefix = prefix;
        this.suffix = suffix;
        this.username = username == null || username.isBlank() ? "username" : username;
        this.active = false;
    }

    public void setUsername(String username) {
        this.username = username == null || username.isBlank() ? "username" : username;
    }

    public String username() { return username; }

    public Text previewText() { return TitlePreview.compose(prefix.toText(), username, suffix.toText()); }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(getX(), getY(), getRight(), getBottom(), 0xFF171B22);
        context.drawBorder(getX(), getY(), getWidth(), getHeight(), 0xFF566273);
        Text value = previewText();
        int width = textRenderer.getWidth(value);
        int drawX = getX() + Math.max(6, (getWidth() - width) / 2);
        int drawY = getY() + Math.max(2, (getHeight() - textRenderer.fontHeight) / 2);
        context.enableScissor(getX() + 2, getY() + 2, getRight() - 2, getBottom() - 2);
        context.drawTextWithShadow(textRenderer, value, drawX, drawY, 0xFFFFFFFF);
        context.disableScissor();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) { return false; }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        builder.put(net.minecraft.client.gui.screen.narration.NarrationPart.TITLE, getMessage());
    }
}
