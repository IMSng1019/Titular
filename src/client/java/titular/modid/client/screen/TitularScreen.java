package titular.modid.client.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import titular.modid.client.ClientNetworking;
import titular.modid.client.ClientText;
import titular.modid.client.ClientTitularState;
import titular.modid.client.TitularScreenState;
import titular.modid.network.ClientSnapshot;

/** Server-authoritative, guided title UI. */
public class TitularScreen extends Screen {
    private ClientSnapshot snapshot;
    private TitularScreenState state;

    /** Kept as a compatibility mirror for callers that used the old tab field. */
    private TitularScreenState.Tab tab = TitularScreenState.Tab.TITLES;

    public TitularScreen() {
        super(ClientText.text("titular.screen.title"));
    }

    public <T extends net.minecraft.client.gui.Element & net.minecraft.client.gui.Drawable
            & net.minecraft.client.gui.Selectable> T addWidget(T widget) {
        return addDrawableChild(widget);
    }

    @Override
    protected void init() {
        snapshot = ClientTitularState.current();
        state = TitularScreenState.from(snapshot, null);
        tab = state.tab();
        rebuild();
    }

    /** Re-renders the current route after a local setting changes. */
    public void rebuildScreen() {
        if (snapshot == null) snapshot = ClientTitularState.current();
        if (state == null) state = TitularScreenState.from(snapshot, null);
        rebuild();
    }

    /** Opens a top-level route after applying the same permission projection as the state model. */
    public void openPage(TitularScreenState.Page page) {
        if (snapshot == null) return;
        TitularScreenState.Page target = page == null ? TitularScreenState.Page.HOME : page;
        if (!TitularScreenState.pageAllowed(target, snapshot.permissionLevel())) return;
        if (state == null) state = TitularScreenState.from(snapshot, null);
        state = state.route(target);
        tab = state.tab();
        rebuild();
    }

    public TitularScreenState.Page page() {
        return state == null ? TitularScreenState.Page.HOME : state.page();
    }

    public TitularScreenState state() { return state; }

    /** Responsive helpers used by panels that need a wider multi-column layout. */
    public int viewportWidth() { return width; }

    public int centeredPanelX(int panelWidth) { return panelX(panelWidth); }

    public void goHome() { openPage(TitularScreenState.Page.HOME); }

    private void rebuild() {
        clearChildren();
        if (snapshot == null) return;
        TitularScreenState.Page page = state == null ? TitularScreenState.Page.HOME : state.page();
        if (page != TitularScreenState.Page.HOME) addBackButton();

        int x = panelX(220);
        switch (page) {
            case HOME -> new LandingPanel(snapshot).install(this, x, 48);
            case TITLE_SWITCH -> new TitleSelectionPanel(snapshot,
                    state == null ? null : state.selectedTitle()).install(this, x, 48);
            case PRIMARY_GROUP -> new SelfGroupPanel(snapshot).install(this, x, 48);
            case LANGUAGE -> new LanguageSettingsPanel().install(this, x, 52);
            case MANAGEMENT -> installManagementPage(x, 64);
        }
    }

    private void installManagementPage(int x, int y) {
        TitularScreenState.Tab selectedTab = state == null
                ? TitularScreenState.Tab.TITLE_EDITOR : state.tab();
        if (selectedTab != TitularScreenState.Tab.PLAYERS
                && selectedTab != TitularScreenState.Tab.GROUPS
                && selectedTab != TitularScreenState.Tab.TITLE_EDITOR
                && selectedTab != TitularScreenState.Tab.SETTINGS) {
            selectedTab = TitularScreenState.Tab.TITLE_EDITOR;
            setTab(selectedTab, false);
        }
        int tabY = 32;
        TitularScreenState.Tab[] managementTabs = {
                TitularScreenState.Tab.PLAYERS,
                TitularScreenState.Tab.GROUPS,
                TitularScreenState.Tab.TITLE_EDITOR,
                TitularScreenState.Tab.SETTINGS
        };
        int gap = 4;
        int buttonWidth = Math.max(44, Math.min(118,
                (width - 16 - gap * (managementTabs.length - 1)) / managementTabs.length));
        int total = buttonWidth * managementTabs.length + gap * (managementTabs.length - 1);
        int startX = Math.max(8, (width - total) / 2);
        for (int index = 0; index < managementTabs.length; index++) {
            TitularScreenState.Tab target = managementTabs[index];
            int buttonX = startX + index * (buttonWidth + gap);
            ButtonWidget button = ButtonWidget.builder(
                            ClientText.text("titular.screen.tab." + target.name().toLowerCase()),
                            ignored -> setTab(target, true))
                    .dimensions(buttonX, tabY, buttonWidth, 20)
                    .build();
            button.active = target != selectedTab;
            addDrawableChild(button);
        }

        switch (selectedTab) {
            case PLAYERS -> new PlayerManagementPanel(snapshot).install(this, x, y);
            case GROUPS -> new GroupManagementPanel(snapshot).install(this, x, y);
            case TITLE_EDITOR -> new TitleManagementPanel(snapshot).install(this, x, y);
            case SETTINGS -> new SettingsPanel(snapshot).install(this, x, y);
            default -> { }
        }
    }

    private void setTab(TitularScreenState.Tab target, boolean rebuild) {
        if (state == null || target == null) return;
        state = new TitularScreenState(state.revision(), state.selectedTitle(), target,
                TitularScreenState.Page.MANAGEMENT);
        tab = target;
        if (rebuild) rebuild();
    }

    private void addBackButton() {
        ButtonWidget back = ButtonWidget.builder(ClientText.text("titular.screen.back"), ignored -> goHome())
                .dimensions(8, 8, 82, 20)
                .build();
        addDrawableChild(back);
    }

    private int panelX(int panelWidth) {
        if (width <= panelWidth + 16) return 8;
        return Math.max(8, width / 2 - panelWidth / 2);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        ClientSnapshot latest = ClientTitularState.current();
        if (latest != null && (snapshot == null || latest != snapshot
                || latest.revision() != snapshot.revision())) {
            state = TitularScreenState.from(latest, state);
            snapshot = latest;
            tab = state.tab();
            rebuild();
        }
        renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer,
                ClientText.text("titular.screen.title"), width / 2, 10, 0xFFFFFFFF);
        if (snapshot != null) {
            String active = state == null || state.selectedTitle() == null ? "-" : state.selectedTitle();
            context.drawCenteredTextWithShadow(textRenderer,
                    ClientText.text("titular.screen.active", active), width / 2, 22, 0xFFB0B0B0);
            TitularScreenState.Page currentPage = page();
            if (currentPage != TitularScreenState.Page.HOME
                    && currentPage != TitularScreenState.Page.MANAGEMENT) {
                context.drawCenteredTextWithShadow(textRenderer, pageTitle(currentPage), width / 2,
                        36, 0xFFE0E0E0);
            }
        }
        String error = ClientNetworking.lastError();
        if (error != null && !error.isBlank()) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(error), width / 2,
                    height - 24, 0xFFFF5555);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    public net.minecraft.client.font.TextRenderer getTextRenderer() { return textRenderer; }

    private Text pageTitle(TitularScreenState.Page page) {
        return switch (page) {
            case TITLE_SWITCH -> ClientText.text("titular.screen.tab.titles");
            case PRIMARY_GROUP -> ClientText.text("titular.screen.tab.self_group");
            case LANGUAGE -> ClientText.text("titular.screen.language.title");
            default -> ClientText.text("titular.screen.home");
        };
    }
}
