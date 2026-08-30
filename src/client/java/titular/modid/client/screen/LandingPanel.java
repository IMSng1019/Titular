package titular.modid.client.screen;

import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import titular.modid.client.ClientText;
import titular.modid.client.TitularScreenState;
import titular.modid.network.ClientSnapshot;

/** Home-page actions for the guided Titular screen. */
public final class LandingPanel {
    private final ClientSnapshot snapshot;

    public LandingPanel(ClientSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public void install(TitularScreen screen, int x, int y) {
        if (snapshot == null) return;
        int row = y;
        for (LandingAction action : TitularScreenState.actions(snapshot.permissionLevel())) {
            int buttonY = row;
            Text label = ClientText.text(action.labelKey());
            ButtonWidget button = ButtonWidget.builder(label,
                    ignored -> screen.openPage(action.page()))
                    .dimensions(x, buttonY, 220, 24)
                    .build();
            screen.addWidget(button);
            row += 30;
        }
    }
}
