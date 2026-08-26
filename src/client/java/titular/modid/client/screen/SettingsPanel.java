package titular.modid.client.screen;

import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import titular.modid.client.ClientNetworking;
import titular.modid.model.DisplayMode;
import titular.modid.network.ClientSnapshot;

/** Superadmin display-mode controls. */
public final class SettingsPanel {
    private final ClientSnapshot snapshot;

    public SettingsPanel(ClientSnapshot snapshot) { this.snapshot = snapshot; }

    public void install(TitularScreen screen, int x, int y) {
        if (!snapshot.canManageAll()) return;
        int row = y;
        for (DisplayMode mode : DisplayMode.values()) {
            int buttonY = row;
            ButtonWidget button = ButtonWidget.builder(Text.literal((mode == snapshot.mode() ? "* " : "") + mode.name()),
                    ignored -> ClientNetworking.sendDisplayMode(mode)).dimensions(x, buttonY, 220, 20).build();
            button.active = mode != snapshot.mode();
            screen.addWidget(button);
            row += 24;
        }
    }
}
