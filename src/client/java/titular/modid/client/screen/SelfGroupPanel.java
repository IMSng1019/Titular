package titular.modid.client.screen;

import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import titular.modid.client.ClientNetworking;
import titular.modid.network.ClientSnapshot;

/** Admin control for changing only the local player's primary group. */
public final class SelfGroupPanel {
    private final ClientSnapshot snapshot;

    public SelfGroupPanel(ClientSnapshot snapshot) { this.snapshot = snapshot; }

    public void install(TitularScreen screen, int x, int y) {
        if (!snapshot.canManageSelfGroup() || snapshot.management().isEmpty()) return;
        String current = snapshot.self() == null ? null : snapshot.self().primaryGroup();
        int row = y;
        for (String id : snapshot.management().get().groupIds()) {
            int buttonY = row;
            ButtonWidget button = ButtonWidget.builder(Text.literal((id.equals(current) ? "* " : "") + id),
                    ignored -> ClientNetworking.sendPrimaryGroup(null, id)).dimensions(x, buttonY, 220, 20).build();
            button.active = !id.equals(current);
            screen.addWidget(button);
            row += 24;
        }
    }
}
