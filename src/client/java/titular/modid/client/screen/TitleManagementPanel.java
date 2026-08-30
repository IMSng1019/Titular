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
import titular.modid.client.editor.TitlePreview;
import titular.modid.model.TitleDefinition;
import titular.modid.network.ClientSnapshot;
import titular.modid.network.TitularRequest;

/** Superadmin title CRUD controls backed by the rich-text editor. */
public final class TitleManagementPanel {
    private final ClientSnapshot snapshot;

    public TitleManagementPanel(ClientSnapshot snapshot) { this.snapshot = snapshot; }

    public void install(TitularScreen screen, int x, int y) {
        if (!snapshot.canManageAll()) return;
        boolean wide = screen.viewportWidth() >= 700;
        int editorWidth = 220;
        int totalWidth = wide ? editorWidth * 3 + 20 : editorWidth;
        int origin = screen.centeredPanelX(totalWidth);
        int idX = screen.centeredPanelX(editorWidth);
        int prefixX = origin;
        int previewX = wide ? origin + editorWidth + 10 : origin;
        int suffixX = wide ? origin + (editorWidth + 10) * 2 : origin;
        int idY = y;
        int editorY = wide ? y + 30 : y + 30;
        int prefixToolbarY = wide ? y + 54 : y + 54;
        int suffixY = wide ? editorY : y + 94;
        int suffixToolbarY = wide ? prefixToolbarY : y + 118;
        int previewY = wide ? editorY : y + 158;
        int buttonsY = wide ? y + 96 : y + 190;

        TextFieldWidget id = new TextFieldWidget(screen.getTextRenderer(), idX, idY, editorWidth, 20,
                ClientText.text("titular.screen.title_id"));
        id.setMaxLength(256); screen.addWidget(id);
        StyledTextDocument prefixDocument = new StyledTextDocument();
        StyledTextDocument suffixDocument = new StyledTextDocument();
        RichTextEditorWidget prefix = new RichTextEditorWidget(screen.getTextRenderer(), prefixX, editorY, editorWidth, 22,
                prefixDocument, ClientText.text("titular.editor.prefix"));
        RichTextEditorWidget suffix = new RichTextEditorWidget(screen.getTextRenderer(), suffixX, suffixY, editorWidth, 22,
                suffixDocument, ClientText.text("titular.editor.suffix"));
        screen.addWidget(prefix); screen.addWidget(suffix);
        FormattingToolbar prefixToolbar = new FormattingToolbar(prefixX, prefixToolbarY, prefixDocument);
        FormattingToolbar suffixToolbar = new FormattingToolbar(suffixX, suffixToolbarY, suffixDocument);
        screen.addWidget(prefixToolbar); screen.addWidget(suffixToolbar);
        java.util.UUID target = snapshot.self() == null ? null : snapshot.self().playerId();
        String username = TitlePreview.resolveUsername(snapshot, target, currentUsername(),
                target == null ? "username" : target.toString());
        TitlePreviewWidget preview = new TitlePreviewWidget(screen.getTextRenderer(), previewX, previewY, editorWidth, 26,
                prefixDocument, username, suffixDocument);
        screen.addWidget(preview);
        ButtonWidget create = ButtonWidget.builder(ClientText.text("titular.screen.create"), ignored -> send(screen, id, prefixDocument, suffixDocument, true))
                .dimensions(idX, buttonsY, 70, 20).build();
        ButtonWidget update = ButtonWidget.builder(ClientText.text("titular.screen.update"), ignored -> send(screen, id, prefixDocument, suffixDocument, false))
                .dimensions(idX + 75, buttonsY, 70, 20).build();
        ButtonWidget delete = ButtonWidget.builder(ClientText.text("titular.screen.delete"), ignored -> {
            if (!id.getText().isBlank()) ClientNetworking.send(TitularRequest.deleteTitle(id.getText().trim(), snapshot.revision()));
        }).dimensions(idX + 150, buttonsY, 70, 20).build();
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
