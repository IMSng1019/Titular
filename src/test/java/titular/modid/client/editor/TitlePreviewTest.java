package titular.modid.client.editor;

import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.junit.jupiter.api.Test;
import titular.modid.model.DisplayMode;
import titular.modid.model.PermissionLevel;
import titular.modid.model.PlayerTitleState;
import titular.modid.network.ClientSnapshot;
import titular.modid.network.OnlineDisplayEntry;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TitlePreviewTest {
    private static final UUID SELF = UUID.randomUUID();
    private static final UUID OFFLINE = UUID.randomUUID();

    @Test
    void composesIndependentPrefixUsernameAndSuffix() {
        Text prefix = Text.literal("[").setStyle(Style.EMPTY.withColor(Formatting.GOLD).withBold(true));
        Text suffix = Text.literal("]").setStyle(Style.EMPTY.withColor(Formatting.AQUA));

        Text preview = TitlePreview.compose(prefix, "Alice", suffix);

        assertEquals("[Alice]", preview.getString());
        assertTrue(preview.getSiblings().size() >= 3);
        assertEquals("Alice", preview.getSiblings().get(1).getString());
        assertEquals(Style.EMPTY, preview.getSiblings().get(1).getStyle());
        assertEquals("gold", preview.getSiblings().get(0).getStyle().getColor().getName());
        assertEquals("aqua", preview.getSiblings().get(2).getStyle().getColor().getName());
        assertEquals("[", prefix.getString());
    }

    @Test
    void allowsEmptySidesAndDoesNotMutateInputs() {
        Text suffix = Text.literal("!").setStyle(Style.EMPTY.withBold(true));
        String before = suffix.getString();

        Text preview = TitlePreview.compose(Text.empty(), "Bob", suffix);

        assertEquals("Bob!", preview.getString());
        assertEquals(before, suffix.getString());
        assertTrue(preview.getSiblings().stream().anyMatch(part -> part.getString().equals("Bob")));
    }

    @Test
    void resolvesOnlineThenSessionThenUuidFallback() {
        ClientSnapshot snapshot = new ClientSnapshot(1, DisplayMode.PREFIX,
                new PlayerTitleState(SELF), List.of(),
                List.of(new OnlineDisplayEntry(SELF, Text.literal("OnlineName"), null)), PermissionLevel.SUPERADMIN,
                true, true, null);

        assertEquals("OnlineName", TitlePreview.resolveUsername(snapshot, SELF, "SessionName", "fallback"));
        assertEquals("SessionName", TitlePreview.resolveUsername(snapshot, OFFLINE, "SessionName", "fallback"));
        assertEquals("fallback", TitlePreview.resolveUsername(snapshot, OFFLINE, "", "fallback"));
        assertEquals(OFFLINE.toString(), TitlePreview.resolveUsername(snapshot, OFFLINE, "", ""));
    }
}
