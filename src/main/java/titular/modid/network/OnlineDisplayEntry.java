package titular.modid.network;

import net.minecraft.text.Text;
import titular.modid.model.TitleDefinition;

import java.util.Objects;
import java.util.UUID;

/** The minimum server-authoritative data needed to render one online player. */
public record OnlineDisplayEntry(UUID playerId, Text rawName, TitleDefinition activeTitle) {
    public OnlineDisplayEntry {
        playerId = Objects.requireNonNull(playerId, "playerId");
        rawName = Objects.requireNonNull(rawName, "rawName").copy();
        if (activeTitle != null) activeTitle = new TitleDefinition(activeTitle.id(), activeTitle.prefix(), activeTitle.suffix());
    }

    @Override public Text rawName() { return rawName.copy(); }
}
