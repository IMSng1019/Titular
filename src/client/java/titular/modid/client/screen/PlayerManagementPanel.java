package titular.modid.client.screen;

import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import titular.modid.client.ClientNetworking;
import titular.modid.model.PlayerTitleState;
import titular.modid.network.ClientSnapshot;
import titular.modid.network.TitularRequest;

import java.util.List;
import java.util.UUID;

/** Superadmin editor for online and offline player fields. */
public final class PlayerManagementPanel {
    private final ClientSnapshot snapshot;

    public PlayerManagementPanel(ClientSnapshot snapshot) { this.snapshot = snapshot; }

    public void install(TitularScreen screen, int x, int y) {
        if (!snapshot.canManageAll()) return;
        TextFieldWidget uuid = field(screen, x, y, 220, "player UUID");
        TextFieldWidget primary = field(screen, x, y + 24, 220, "primary group");
        TextFieldWidget groups = field(screen, x, y + 48, 220, "extra groups (comma separated)");
        TextFieldWidget titles = field(screen, x, y + 72, 220, "extra titles (comma separated)");
        TextFieldWidget active = field(screen, x, y + 96, 220, "active title");
        ButtonWidget load = ButtonWidget.builder(Text.translatable("titular.screen.load"), ignored -> {
            try {
                UUID id = UUID.fromString(uuid.getText().trim());
                PlayerTitleState state = snapshot.playerStates().get(id);
                if (state == null) return;
                primary.setText(safe(state.primaryGroup()));
                groups.setText(String.join(",", state.extraGroups()));
                titles.setText(String.join(",", state.extraTitles()));
                active.setText(safe(state.activeTitle()));
            } catch (IllegalArgumentException ignoredException) { }
        }).dimensions(x, y + 120, 106, 20).build();
        screen.addWidget(load);
        ButtonWidget save = ButtonWidget.builder(Text.translatable("titular.screen.save"), ignored -> {
            try {
                UUID id = UUID.fromString(uuid.getText().trim());
                ClientNetworking.sendPlayerFields(id, new TitularRequest.PlayerFields(nullable(primary.getText()), split(groups.getText()),
                        split(titles.getText()), nullable(active.getText())));
            } catch (IllegalArgumentException ignoredException) { }
        }).dimensions(x + 114, y + 120, 106, 20).build();
        screen.addWidget(save);
    }

    private static TextFieldWidget field(TitularScreen screen, int x, int y, int width, String placeholder) {
        TextFieldWidget field = new TextFieldWidget(screen.getTextRenderer(), x, y, width, 20, Text.literal(placeholder));
        field.setMaxLength(1024);
        screen.addWidget(field);
        return field;
    }

    private static String safe(String value) { return value == null ? "" : value; }
    private static String nullable(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static List<String> split(String value) { return List.of(value.split(",")).stream().map(String::trim).filter(s -> !s.isEmpty()).toList(); }
}
