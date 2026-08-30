package titular.modid.client.screen;

import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import titular.modid.client.ClientNetworking;
import titular.modid.client.ClientText;
import titular.modid.model.GroupDefinition;
import titular.modid.network.ClientSnapshot;
import titular.modid.network.TitularRequest;

import java.util.List;

/** Superadmin group CRUD controls. */
public final class GroupManagementPanel {
    private final ClientSnapshot snapshot;

    public GroupManagementPanel(ClientSnapshot snapshot) { this.snapshot = snapshot; }

    public void install(TitularScreen screen, int x, int y) {
        if (!snapshot.canManageAll()) return;
        TextFieldWidget id = field(screen, x, y, "titular.screen.group_id");
        TextFieldWidget parent = field(screen, x, y + 24, "titular.screen.parent_id");
        TextFieldWidget titleIds = field(screen, x, y + 48, "titular.screen.title_ids");
        ButtonWidget create = ButtonWidget.builder(ClientText.text("titular.screen.create"), ignored -> {
            String groupId = id.getText().trim();
            if (!groupId.isEmpty()) ClientNetworking.send(TitularRequest.createGroup(group(groupId, parent.getText(), titleIds.getText()), snapshot.revision()));
        }).dimensions(x, y + 72, 70, 20).build();
        ButtonWidget update = ButtonWidget.builder(ClientText.text("titular.screen.update"), ignored -> {
            String groupId = id.getText().trim();
            if (!groupId.isEmpty()) ClientNetworking.send(TitularRequest.updateGroup(group(groupId, parent.getText(), titleIds.getText()), snapshot.revision()));
        }).dimensions(x + 75, y + 72, 70, 20).build();
        ButtonWidget delete = ButtonWidget.builder(ClientText.text("titular.screen.delete"), ignored -> {
            String groupId = id.getText().trim();
            if (!groupId.isEmpty()) ClientNetworking.send(TitularRequest.deleteGroup(groupId, snapshot.revision()));
        }).dimensions(x + 150, y + 72, 70, 20).build();
        screen.addWidget(create); screen.addWidget(update); screen.addWidget(delete);
    }

    private static GroupDefinition group(String id, String parent, String titles) {
        return new GroupDefinition(id, parent.isBlank() ? null : parent.trim(), split(titles));
    }
    private static TextFieldWidget field(TitularScreen screen, int x, int y, String hintKey) {
        TextFieldWidget field = new TextFieldWidget(screen.getTextRenderer(), x, y, 220, 20,
                ClientText.text(hintKey));
        field.setMaxLength(1024); screen.addWidget(field); return field;
    }
    private static List<String> split(String value) { return List.of(value.split(",")).stream().map(String::trim).filter(s -> !s.isEmpty()).toList(); }
}
