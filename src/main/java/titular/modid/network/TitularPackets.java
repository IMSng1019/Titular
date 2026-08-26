package titular.modid.network;

import net.minecraft.util.Identifier;

import titular.modid.Titular;

/** Stable packet identifiers shared by the client and server. */
public final class TitularPackets {
    public static final Identifier SNAPSHOT = Titular.id("snapshot");
    public static final Identifier REQUEST = Titular.id("request");
    public static final Identifier ERROR = Titular.id("error");
    public static final Identifier OPEN = Titular.id("open");
    public static final Identifier SNAPSHOT_ID = SNAPSHOT;
    public static final Identifier REQUEST_ID = REQUEST;
    private TitularPackets() {}
}
