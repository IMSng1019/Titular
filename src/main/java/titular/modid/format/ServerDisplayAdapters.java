package titular.modid.format;

import net.minecraft.text.Text;
import titular.modid.model.TitleDefinition;
import titular.modid.service.TitularService;

import java.util.UUID;

/** Server-side chat adapter; callers supply the authoritative service. */
public final class ServerDisplayAdapters {
    private ServerDisplayAdapters() { }

    public static Text formatChatName(Text rawName, UUID playerId, TitularService service) {
        if (rawName == null || service == null || playerId == null) {
            return rawName == null ? Text.empty() : rawName.copy();
        }
        TitleDefinition title = service.resolveActiveTitle(playerId).orElse(null);
        return TitularFormatter.format(rawName, title, service.data().settings().displayMode());
    }
}
