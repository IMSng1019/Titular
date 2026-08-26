package titular.modid.network;

import net.minecraft.network.PacketByteBuf;
import titular.modid.model.*;

import java.util.*;

/** Explicit bounded wire codec for actor-independent client requests. */
public final class RequestCodec {
    private RequestCodec() {}
    public static void encode(PacketByteBuf b, TitularRequest r) { write(b, r); }
    public static TitularRequest decode(PacketByteBuf b) { return read(b); }
    public static void write(PacketByteBuf b, TitularRequest r) {
        if (r == null) throw new IllegalArgumentException("request is null");
        validateTarget(r.operation(), r.target());
        validateRevision(r.expectedRevision());
        b.writeVarInt(r.operation().ordinal()); b.writeBoolean(r.target()!=null); if(r.target()!=null)b.writeUuid(r.target());
        b.writeLong(r.expectedRevision());
        switch(r.operation()) {
            case ACTIVATE -> SnapshotCodec.writeNullableString(b,r.titleId());
            case SET_PRIMARY_GROUP, DELETE_GROUP, DELETE_TITLE -> SnapshotCodec.writeNullableString(b,r.id());
            case CREATE_GROUP, UPDATE_GROUP -> writeGroup(b,r.group());
            case CREATE_TITLE, UPDATE_TITLE -> writeTitle(b,r.title());
            case SET_PLAYER_FIELDS -> writeFields(b,r.fields());
            case SET_DISPLAY_MODE -> { if(r.mode()==null)throw new IllegalArgumentException("mode is null"); b.writeVarInt(r.mode().ordinal()); }
            case CLEAR, REFRESH, RELOAD -> { }
        }
    }
    public static TitularRequest read(PacketByteBuf b) {
        try {
            int op=b.readVarInt(); TitularRequest.Operation[] ops=TitularRequest.Operation.values(); if(op<0||op>=ops.length)throw new IllegalArgumentException("unknown operation");
            UUID target=b.readBoolean()?b.readUuid():null;
            TitularRequest.Operation operation = ops[op];
            validateTarget(operation, target);
            long expectedRevision = b.readLong();
            validateRevision(expectedRevision);
            TitularRequest result = switch(ops[op]) {
                case ACTIVATE -> TitularRequest.activate(SnapshotCodec.readNullableString(b), expectedRevision);
                case CLEAR -> TitularRequest.clear(expectedRevision);
                case SET_PRIMARY_GROUP -> TitularRequest.setPrimaryGroup(target,SnapshotCodec.readNullableString(b), expectedRevision);
                case CREATE_GROUP -> TitularRequest.createGroup(readGroup(b), expectedRevision);
                case UPDATE_GROUP -> TitularRequest.updateGroup(readGroup(b), expectedRevision);
                case DELETE_GROUP -> TitularRequest.deleteGroup(SnapshotCodec.readNullableString(b), expectedRevision);
                case CREATE_TITLE -> TitularRequest.createTitle(readTitle(b), expectedRevision);
                case UPDATE_TITLE -> TitularRequest.updateTitle(readTitle(b), expectedRevision);
                case DELETE_TITLE -> TitularRequest.deleteTitle(SnapshotCodec.readNullableString(b), expectedRevision);
                case SET_PLAYER_FIELDS -> TitularRequest.setPlayerFields(target,readFields(b), expectedRevision);
                case SET_DISPLAY_MODE -> { int mode=b.readVarInt(); DisplayMode[] values=DisplayMode.values(); if(mode<0||mode>=values.length)throw new IllegalArgumentException("unknown display mode"); yield TitularRequest.setDisplayMode(values[mode], expectedRevision); }
                case REFRESH -> TitularRequest.refresh(expectedRevision);
                case RELOAD -> TitularRequest.reload(expectedRevision);
            };
            if (b.isReadable()) throw new IllegalArgumentException("trailing request data");
            return result;
        } catch (RuntimeException e) {
            if (e instanceof IllegalArgumentException) throw e;
            throw new IllegalArgumentException("Malformed request", e);
        }
    }
    public static String encodeToString(TitularRequest r) { return r.operation().name(); }
    private static void validateTarget(TitularRequest.Operation operation, UUID target) {
        boolean allowed = operation == TitularRequest.Operation.SET_PRIMARY_GROUP
                || operation == TitularRequest.Operation.SET_PLAYER_FIELDS;
        if (!allowed && target != null) throw new IllegalArgumentException("unexpected target for operation " + operation);
    }
    private static void validateRevision(long revision) {
        if (revision < 0L) throw new IllegalArgumentException("expected revision must be non-negative");
    }
    private static void writeGroup(PacketByteBuf b,GroupDefinition g){if(g==null)throw new IllegalArgumentException("group is null");SnapshotCodec.writeString(b,g.id());SnapshotCodec.writeNullableString(b,g.parent());SnapshotCodec.writeStrings(b,g.titleIds());}
    private static GroupDefinition readGroup(PacketByteBuf b){return new GroupDefinition(SnapshotCodec.readString(b),SnapshotCodec.readNullableString(b),SnapshotCodec.readStrings(b));}
    private static void writeTitle(PacketByteBuf b,TitleDefinition t){if(t==null)throw new IllegalArgumentException("title is null");SnapshotCodec.writeString(b,t.id());SnapshotCodec.writeText(b,t.prefix());SnapshotCodec.writeText(b,t.suffix());}
    private static TitleDefinition readTitle(PacketByteBuf b){return new TitleDefinition(SnapshotCodec.readString(b),SnapshotCodec.readText(b),SnapshotCodec.readText(b));}
    private static void writeFields(PacketByteBuf b,TitularRequest.PlayerFields f){if(f==null)throw new IllegalArgumentException("fields is null");SnapshotCodec.writeNullableString(b,f.primaryGroup());SnapshotCodec.writeStrings(b,f.extraGroups());SnapshotCodec.writeStrings(b,f.extraTitles());SnapshotCodec.writeNullableString(b,f.activeTitle());}
    private static TitularRequest.PlayerFields readFields(PacketByteBuf b){return new TitularRequest.PlayerFields(SnapshotCodec.readNullableString(b),SnapshotCodec.readStrings(b),SnapshotCodec.readStrings(b),SnapshotCodec.readNullableString(b));}
}
