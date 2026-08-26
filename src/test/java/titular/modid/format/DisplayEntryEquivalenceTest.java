package titular.modid.format;

import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import titular.modid.codec.TextJsonCodec;
import titular.modid.model.DisplayMode;
import titular.modid.model.PermissionLevel;
import titular.modid.model.TitleDefinition;
import titular.modid.network.ClientSnapshot;
import titular.modid.network.OnlineDisplayEntry;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DisplayEntryEquivalenceTest {
    private static final UUID PLAYER = UUID.randomUUID();

    @AfterEach
    void clearDisplaySnapshot() {
        DisplayAdapters.clearSnapshot();
    }

    @Test
    void headTabAndChatUseEquivalentFormattedText() {
        Text name = Text.literal("Ada").formatted(Formatting.AQUA);
        TitleDefinition title = new TitleDefinition("vip",
                Text.literal("[").formatted(Formatting.GOLD),
                Text.literal("]").formatted(Formatting.RED));
        ClientSnapshot snapshot = new ClientSnapshot(4L, DisplayMode.BOTH, null, List.of(),
                List.of(new OnlineDisplayEntry(PLAYER, name, title)), PermissionLevel.PLAYER);
        DisplayAdapters.replaceSnapshot(snapshot);

        String head = TextJsonCodec.encode(DisplayAdapters.formatHeadName(name, PLAYER));
        String tab = TextJsonCodec.encode(DisplayAdapters.formatTabName(name, PLAYER));
        String chat = TextJsonCodec.encode(DisplayAdapters.formatChatName(name, PLAYER));

        assertEquals(head, tab);
        assertEquals(tab, chat);
        assertEquals(TextJsonCodec.encode(TitularFormatter.format(name, title, DisplayMode.BOTH)), head);
    }

    @Test
    void missingSnapshotOrTitleReturnsACloneOfOriginalName() {
        Text name = Text.literal("Ada").formatted(Formatting.DARK_GREEN);
        assertEquals(TextJsonCodec.encode(name), TextJsonCodec.encode(DisplayAdapters.formatHeadName(name, PLAYER)));

        DisplayAdapters.replaceSnapshot(new ClientSnapshot(1L, DisplayMode.PREFIX, null, List.of(),
                List.of(new OnlineDisplayEntry(PLAYER, name, null)), PermissionLevel.PLAYER));
        assertEquals(TextJsonCodec.encode(name), TextJsonCodec.encode(DisplayAdapters.formatTabName(name, PLAYER)));
    }
}
