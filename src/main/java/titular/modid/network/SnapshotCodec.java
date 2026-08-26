package titular.modid.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import titular.modid.codec.TextJsonCodec;
import titular.modid.model.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Explicit bounded wire codec for server snapshots. */
public final class SnapshotCodec {
    private static final int MAX_STRING = TitularLimits.MAX_STRING_LENGTH,
            MAX_LIST = TitularLimits.MAX_REFERENCE_LIST,
            MAX_TEXT = TitularLimits.MAX_TEXT_JSON_LENGTH;
    private SnapshotCodec() {}
    public static void encode(PacketByteBuf b, ClientSnapshot s) { write(b, s); }
    public static ClientSnapshot decode(PacketByteBuf b) { return read(b); }
    public static void write(PacketByteBuf b, ClientSnapshot s) {
        if (s == null) throw new IllegalArgumentException("snapshot is null");
        b.writeLong(s.revision()); writeEnum(b,s.mode()); writeStateNullable(b,s.self()); writeStrings(b,s.availableTitleIds());
        writeListSize(b,s.onlinePlayers().size()); for (OnlineDisplayEntry e:s.onlinePlayers()) { b.writeUuid(e.playerId()); writeText(b,e.rawName()); writeTitleNullable(b,e.activeTitle()); }
        writeEnum(b,s.permissionLevel()); b.writeBoolean(s.canManageSelfGroup()); b.writeBoolean(s.canManageAll());
        b.writeBoolean(s.management().isPresent()); if (s.management().isPresent()) { var m=s.management().get(); writeStrings(b,m.groupIds()); writeStrings(b,m.titleIds()); writeUuids(b,m.playerIds()); b.writeBoolean(m.settings()!=null); if(m.settings()!=null) writeEnum(b,m.settings().displayMode()); }
        writeMapSize(b,s.titles().size()); for (var e:s.titles().entrySet()) { writeString(b,e.getKey()); writeTitle(b,e.getValue()); }
        writeMapSize(b,s.groups().size()); for (var e:s.groups().entrySet()) { writeString(b,e.getKey()); writeGroup(b,e.getValue()); }
        writeMapSize(b,s.playerStates().size()); for (var e:s.playerStates().entrySet()) { b.writeUuid(e.getKey()); writeState(b,e.getValue()); }
    }
    public static ClientSnapshot read(PacketByteBuf b) {
        try {
            long revision=b.readLong(); DisplayMode mode=readEnum(b,DisplayMode.values()); PlayerTitleState self=readStateNullable(b); List<String> ids=readStrings(b);
            int n=readListSize(b); List<OnlineDisplayEntry> online=new ArrayList<>(); for(int i=0;i<n;i++) online.add(new OnlineDisplayEntry(b.readUuid(),readText(b),readTitleNullable(b)));
            PermissionLevel level=readEnum(b,PermissionLevel.values()); boolean own=b.readBoolean(), all=b.readBoolean(); Optional<ClientSnapshot.ManagementData> management=Optional.empty();
            if(b.readBoolean()) { List<String> groups=readStrings(b), titles=readStrings(b); List<UUID> players=readUuids(b); TitularSettings settings=b.readBoolean()?new TitularSettings(readEnum(b,DisplayMode.values())):null; management=Optional.of(new ClientSnapshot.ManagementData(groups,titles,players,settings)); }
            Map<String,TitleDefinition> titles=new LinkedHashMap<>(); int tc=readMapSize(b); for(int i=0;i<tc;i++){String id=readString(b); TitleDefinition t=readTitle(b); if(!id.equals(t.id())) throw new IllegalArgumentException("title map key mismatch"); if(titles.putIfAbsent(id,t)!=null) throw new IllegalArgumentException("duplicate title map key");}
            Map<String,GroupDefinition> groups=new LinkedHashMap<>(); int gc=readMapSize(b); for(int i=0;i<gc;i++){String id=readString(b); GroupDefinition g=readGroup(b); if(!id.equals(g.id())) throw new IllegalArgumentException("group map key mismatch"); if(groups.putIfAbsent(id,g)!=null) throw new IllegalArgumentException("duplicate group map key");}
            Map<UUID,PlayerTitleState> players=new LinkedHashMap<>(); int pc=readMapSize(b); for(int i=0;i<pc;i++){UUID id=b.readUuid(); PlayerTitleState p=readState(b); if(!id.equals(p.playerId())) throw new IllegalArgumentException("player map key mismatch"); if(players.putIfAbsent(id,p)!=null) throw new IllegalArgumentException("duplicate player map key");}
            if (b.isReadable()) throw new IllegalArgumentException("trailing snapshot data");
            return new ClientSnapshot(revision,mode,self,ids,online,level,own,all,management,titles,groups,players);
        } catch (RuntimeException e) {
            if (e instanceof IllegalArgumentException) throw e;
            throw new IllegalArgumentException("Malformed snapshot", e);
        }
    }
    private static void writeTitle(PacketByteBuf b,TitleDefinition t){if(t==null)throw new IllegalArgumentException("null title");writeString(b,t.id());writeText(b,t.prefix());writeText(b,t.suffix());}
    private static TitleDefinition readTitle(PacketByteBuf b){String id=readString(b);return new TitleDefinition(id,readText(b),readText(b));}
    private static void writeTitleNullable(PacketByteBuf b,TitleDefinition t){b.writeBoolean(t!=null);if(t!=null)writeTitle(b,t);}
    private static TitleDefinition readTitleNullable(PacketByteBuf b){return b.readBoolean()?readTitle(b):null;}
    private static void writeGroup(PacketByteBuf b,GroupDefinition g){if(g==null)throw new IllegalArgumentException("null group");writeString(b,g.id());writeNullableString(b,g.parent());writeStrings(b,g.titleIds());}
    private static GroupDefinition readGroup(PacketByteBuf b){return new GroupDefinition(readString(b),readNullableString(b),readStrings(b));}
    private static void writeState(PacketByteBuf b,PlayerTitleState p){if(p==null)throw new IllegalArgumentException("null state");b.writeUuid(p.playerId());writeNullableString(b,p.primaryGroup());writeStrings(b,p.extraGroups());writeStrings(b,p.extraTitles());writeNullableString(b,p.activeTitle());writeStrings(b,p.luckPermsGroups());}
    private static PlayerTitleState readState(PacketByteBuf b){return new PlayerTitleState(b.readUuid(),readNullableString(b),readStrings(b),readStrings(b),readNullableString(b),readStrings(b));}
    private static void writeStateNullable(PacketByteBuf b,PlayerTitleState p){b.writeBoolean(p!=null);if(p!=null)writeState(b,p);}
    private static PlayerTitleState readStateNullable(PacketByteBuf b){return b.readBoolean()?readState(b):null;}
    static void writeText(PacketByteBuf b,Text t){writeStringBounded(b,TextJsonCodec.encode(t),MAX_TEXT);}
    static Text readText(PacketByteBuf b){return TextJsonCodec.decode(readStringBounded(b,MAX_TEXT));}
    static void writeString(PacketByteBuf b,String s){writeStringBounded(b,s,MAX_STRING);} static String readString(PacketByteBuf b){return readStringBounded(b,MAX_STRING);}
    static void writeNullableString(PacketByteBuf b,String s){b.writeBoolean(s!=null);if(s!=null)writeString(b,s);} static String readNullableString(PacketByteBuf b){return b.readBoolean()?readString(b):null;}
    static void writeStrings(PacketByteBuf b,List<String> xs){if(xs==null)xs=List.of();writeListSize(b,xs.size());for(String x:xs)writeString(b,x);}
    static List<String> readStrings(PacketByteBuf b){int n=readListSize(b);List<String> r=new ArrayList<>();for(int i=0;i<n;i++)r.add(readString(b));return List.copyOf(r);}
    private static void writeUuids(PacketByteBuf b,List<UUID> xs){if(xs==null)xs=List.of();writeListSize(b,xs.size());for(UUID x:xs){if(x==null)throw new IllegalArgumentException("null UUID");b.writeUuid(x);}}
    private static List<UUID> readUuids(PacketByteBuf b){int n=readListSize(b);List<UUID> r=new ArrayList<>();for(int i=0;i<n;i++)r.add(b.readUuid());return List.copyOf(r);}
    private static void writeStringBounded(PacketByteBuf b,String s,int max){if(s==null)throw new IllegalArgumentException("null string");if(s.length()>max||s.getBytes(StandardCharsets.UTF_8).length>max*4)throw new IllegalArgumentException("oversized string");b.writeString(s,max);}
    private static String readStringBounded(PacketByteBuf b,int max){String s=b.readString(max);if(s.length()>max)throw new IllegalArgumentException("oversized string");return s;}
    private static void writeListSize(PacketByteBuf b,int n){if(n<0||n>MAX_LIST)throw new IllegalArgumentException("oversized list");b.writeVarInt(n);} private static int readListSize(PacketByteBuf b){int n=b.readVarInt();if(n<0||n>MAX_LIST)throw new IllegalArgumentException("oversized list");return n;}
    private static void writeMapSize(PacketByteBuf b,int n){writeListSize(b,n);} private static int readMapSize(PacketByteBuf b){return readListSize(b);}
    private static <E extends Enum<E>> void writeEnum(PacketByteBuf b,E e){if(e==null)throw new IllegalArgumentException("null enum");b.writeVarInt(e.ordinal());}
    private static <E extends Enum<E>> E readEnum(PacketByteBuf b,E[] values){int i=b.readVarInt();if(i<0||i>=values.length)throw new IllegalArgumentException("unknown enum");return values[i];}
}
