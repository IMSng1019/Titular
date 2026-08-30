package titular.modid.client.screen;

import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import titular.modid.client.ClientNetworking;
import titular.modid.client.ClientText;
import titular.modid.client.editor.RichTextEditorWidget;
import titular.modid.client.editor.StyledTextDocument;
import titular.modid.client.editor.FormattingToolbar;
import titular.modid.client.editor.TitlePreviewWidget;
import titular.modid.model.TitleDefinition;
import titular.modid.network.ClientSnapshot;
import titular.modid.network.TitularRequest;

/** Superadmin title CRUD controls backed by the rich-text editor. */
public final class TitleManagementPanel {
    private final ClientSnapshot snapshot;

    public TitleManagementPanel(ClientSnapshot snapshot) { this.snapshot = snapshot; }

    public void install(TitularScreen screen, int x, int y) {
        if (!snapshot.canManageAll()) return;
        TextFieldWidget id = new TextFieldWidget(screen.getTextRenderer(), x, y, 220, 20,
                ClientText.text("titular.screen.title_id"));
        id.setMaxLength(256); screen.addWidget(id);
        StyledTextDocument prefixDocument = new StyledTextDocument();
        StyledTextDocument suffixDocument = new StyledTextDocument();
        RichTextEditorWidget prefix = new RichTextEditorWidget(screen.getTextRenderer(), x, y + 24, 220, 22, prefixDocument);
        RichTextEditorWidget suffix = new RichTextEditorWidget(screen.getTextRenderer(), x, y + 72, 220, 22, suffixDocument);
        screen.addWidget(prefix); screen.addWidget(suffix);
        FormattingToolbar prefixToolbar = new FormattingToolbar(x, y + 48, prefixDocument);
        FormattingToolbar suffixToolbar = new FormattingToolbar(x, y + 96, suffixDocument);
        screen.addWidget(prefixToolbar); screen.addWidget(suffixToolbar);
        String username = currentUsername();
        TitlePreviewWidget preview = new TitlePreviewWidget(screen.getTextRenderer(), x, y + 122, 220, 26,
                prefixDocument, username, suffixDocument);
        screen.addWidget(preview);
        ButtonWidget create = ButtonWidget.builder(Text.translatable("titular.screen.create"), ignored -> send(screen, id, prefixDocument, suffixDocument, true))
                .dimensions(x, y + 154, 70, 20).build();
        ButtonWidget update = ButtonWidget.builder(ClientText.text("titular.screen.update"), ignored -> send(screen, id, prefixDocument, suffixDocument, false))
                .dimensions(x + 75, y + 154, 70, 20).build();
        ButtonWidget delete = ButtonWidget.builder(ClientText.text("titular.screen.delete"), ignored -> {
            if (!id.getText().isBlank()) ClientNetworking.send(TitularRequest.deleteTitle(id.getText().trim(), snapshot.revision()));
        }).dimensions(x + 150, y + 154, 70, 20).build();
        screen.addWidget(create); screen.addWidget(update); screen.addWidget(delete);
    }

    private static String currentUsername() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.getSession() != null) {
                String username = client.getSession().getUsername();
                if (username != null && !username.isBlank()) return username;
            }
        } catch (RuntimeException ignored) {
            // The editor can still be used in a disconnected screen/test.
        }
        return "username";
    }

    private static void send(TitularScreen screen, TextFieldWidget id, StyledTextDocument prefix, StyledTextDocument suffix, boolean create) {
        if (id.getText().isBlank()) return;
        TitleDefinition title = new TitleDefinition(id.getText().trim(), prefix.toText(), suffix.toText());
        ClientSnapshot snapshot = titular.modid.client.ClientTitularState.current();
        if (snapshot == null) return;
        ClientNetworking.send(create ? TitularRequest.createTitle(title, snapshot.revision()) : TitularRequest.updateTitle(title, snapshot.revision()));
    }
}
