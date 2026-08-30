package titular.modid.client.screen;

import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import titular.modid.client.ClientNetworking;
import titular.modid.client.ClientText;
import titular.modid.network.ClientSnapshot;

/** Player-facing title selection controls. */
public final class TitleSelectionPanel {
    private final ClientSnapshot snapshot;
    private final String selected;

    public TitleSelectionPanel(ClientSnapshot snapshot, String selected) {
        this.snapshot = snapshot;
        this.selected = selected;
    }

    public void install(TitularScreen screen, int x, int y) {
        int row = y;
        for (String id : snapshot.availableTitleIds()) {
            int buttonY = row;
            ButtonWidget button = ButtonWidget.builder(Text.literal((id.equals(selected) ? "* " : "") + id),
                    ignored -> ClientNetworking.sendActivate(id)).dimensions(x, buttonY, 220, 20).build();
            button.active = !id.equals(selected);
            screen.addWidget(button);
            row += 24;
        }
        ButtonWidget clear = ButtonWidget.builder(ClientText.text("titular.screen.clear"),
                ignored -> ClientNetworking.sendClear()).dimensions(x, row, 220, 20).build();
        clear.active = selected != null;
        screen.addWidget(clear);
    }
}
