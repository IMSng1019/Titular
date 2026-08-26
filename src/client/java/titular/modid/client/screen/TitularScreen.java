package titular.modid.client.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import titular.modid.client.ClientTitularState;
import titular.modid.client.TitularScreenState;
import titular.modid.client.ClientNetworking;
import titular.modid.network.ClientSnapshot;

/** Server-authoritative title UI. It only renders projected snapshot data and emits packets. */
public class TitularScreen extends Screen {
    private ClientSnapshot snapshot;
    private TitularScreenState state;
    private TitularScreenState.Tab tab = TitularScreenState.Tab.TITLES;

    public TitularScreen() { super(Text.translatable("titular.screen.title")); }

    public <T extends net.minecraft.client.gui.Element & net.minecraft.client.gui.Drawable & net.minecraft.client.gui.Selectable> T addWidget(T widget) {
        return addDrawableChild(widget);
    }

    @Override
    protected void init() {
        snapshot = ClientTitularState.current();
        state = TitularScreenState.from(snapshot, null);
        rebuild();
    }

    private void rebuild() {
        clearChildren();
        if (snapshot == null) return;
        int tabX = Math.max(8, width / 2 - 330);
        int tabY = 30;
        int offset = 0;
        for (TitularScreenState.Tab available : TitularScreenState.tabs(snapshot.permissionLevel())) {
            final TitularScreenState.Tab target = available;
            ButtonWidget button = ButtonWidget.builder(Text.translatable("titular.screen.tab." + available.name().toLowerCase()),
                    ignored -> { tab = target; rebuild(); }).dimensions(tabX + offset, tabY, 104, 20).build();
            button.active = tab != available;
            addDrawableChild(button);
            offset += 108;
        }
        int x = Math.max(8, width / 2 - 110);
        int y = 62;
        switch (tab) {
            case TITLES -> new TitleSelectionPanel(snapshot, state.selectedTitle()).install(this, x, y);
            case SELF_GROUP -> new SelfGroupPanel(snapshot).install(this, x, y);
            case PLAYERS -> new PlayerManagementPanel(snapshot).install(this, x, y);
            case GROUPS -> new GroupManagementPanel(snapshot).install(this, x, y);
            case TITLE_EDITOR -> new TitleManagementPanel(snapshot).install(this, x, y);
            case SETTINGS -> new SettingsPanel(snapshot).install(this, x, y);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        ClientSnapshot latest = ClientTitularState.current();
        if (latest != null && (snapshot == null || latest != snapshot || latest.revision() != snapshot.revision())) {
            state = TitularScreenState.from(latest, state);
            snapshot = latest;
            if (!TitularScreenState.tabs(snapshot.permissionLevel()).contains(tab)) tab = TitularScreenState.Tab.TITLES;
            rebuild();
        }
        renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, 0xFFFFFFFF);
        if (snapshot != null) {
            String active = state == null || state.selectedTitle() == null ? "-" : state.selectedTitle();
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("titular.screen.active", active), width / 2, 18, 0xFFB0B0B0);
        }
        String error = ClientNetworking.lastError();
        if (error != null && !error.isBlank()) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(error), width / 2, height - 24, 0xFFFF5555);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    public net.minecraft.client.font.TextRenderer getTextRenderer() { return textRenderer; }
}
