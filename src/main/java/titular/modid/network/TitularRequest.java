package titular.modid.network;

import titular.modid.model.*;
import java.util.*;

/** Actor-independent C2S operation. The server derives actor permissions from its connection. */
public final class TitularRequest {
    public enum Operation { ACTIVATE, CLEAR, SET_PRIMARY_GROUP, CREATE_GROUP, UPDATE_GROUP, DELETE_GROUP,
        CREATE_TITLE, UPDATE_TITLE, DELETE_TITLE, SET_PLAYER_FIELDS, SET_DISPLAY_MODE, REFRESH, RELOAD }
    public record PlayerFields(String primaryGroup, List<String> extraGroups, List<String> extraTitles, String activeTitle) {
        public PlayerFields { extraGroups = extraGroups == null ? List.of() : List.copyOf(extraGroups); extraTitles = extraTitles == null ? List.of() : List.copyOf(extraTitles); }
    }
    private final Operation operation; private final UUID target; private final long expectedRevision; private final String id; private final String titleId;
    private final GroupDefinition group; private final TitleDefinition title; private final PlayerFields fields; private final DisplayMode mode;
    private TitularRequest(Operation operation, UUID target, long expectedRevision, String id, String titleId, GroupDefinition group,
                           TitleDefinition title, PlayerFields fields, DisplayMode mode) {
        if (expectedRevision < -1L) throw new IllegalArgumentException("invalid expected revision");
        this.operation = Objects.requireNonNull(operation); this.target = target; this.expectedRevision = expectedRevision; this.id = id; this.titleId = titleId;
        this.group = group; this.title = title; this.fields = fields; this.mode = mode;
    }
    public static TitularRequest activate(String titleId) { return activate(titleId, 0L); }
    public static TitularRequest activate(String titleId, long expectedRevision) { return new TitularRequest(Operation.ACTIVATE, null, expectedRevision, null, titleId, null,null,null,null); }
    public static TitularRequest clear() { return clear(0L); }
    public static TitularRequest clear(long expectedRevision) { return new TitularRequest(Operation.CLEAR,null,expectedRevision,null,null,null,null,null,null); }
    public static TitularRequest setPrimaryGroup(String groupId) { return setPrimaryGroup(null, groupId, 0L); }
    public static TitularRequest setPrimaryGroup(String groupId, long expectedRevision) { return setPrimaryGroup(null, groupId, expectedRevision); }
    public static TitularRequest setPrimaryGroup(UUID target, String groupId) { return setPrimaryGroup(target, groupId, 0L); }
    public static TitularRequest setPrimaryGroup(UUID target, String groupId, long expectedRevision) { return new TitularRequest(Operation.SET_PRIMARY_GROUP,target,expectedRevision,groupId,null,null,null,null,null); }
    public static TitularRequest createGroup(GroupDefinition group) { return createGroup(group, 0L); }
    public static TitularRequest createGroup(GroupDefinition group, long expectedRevision) { return new TitularRequest(Operation.CREATE_GROUP,null,expectedRevision,null,null,group,null,null,null); }
    public static TitularRequest updateGroup(GroupDefinition group) { return updateGroup(group, 0L); }
    public static TitularRequest updateGroup(GroupDefinition group, long expectedRevision) { return new TitularRequest(Operation.UPDATE_GROUP,null,expectedRevision,null,null,group,null,null,null); }
    public static TitularRequest deleteGroup(String id) { return deleteGroup(id, 0L); }
    public static TitularRequest deleteGroup(String id, long expectedRevision) { return new TitularRequest(Operation.DELETE_GROUP,null,expectedRevision,id,null,null,null,null,null); }
    public static TitularRequest createTitle(TitleDefinition title) { return createTitle(title, 0L); }
    public static TitularRequest createTitle(TitleDefinition title, long expectedRevision) { return new TitularRequest(Operation.CREATE_TITLE,null,expectedRevision,null,null,null,title,null,null); }
    public static TitularRequest updateTitle(TitleDefinition title) { return updateTitle(title, 0L); }
    public static TitularRequest updateTitle(TitleDefinition title, long expectedRevision) { return new TitularRequest(Operation.UPDATE_TITLE,null,expectedRevision,null,null,null,title,null,null); }
    public static TitularRequest deleteTitle(String id) { return deleteTitle(id, 0L); }
    public static TitularRequest deleteTitle(String id, long expectedRevision) { return new TitularRequest(Operation.DELETE_TITLE,null,expectedRevision,id,null,null,null,null,null); }
    public static TitularRequest setPlayerFields(UUID target, PlayerFields fields) { return setPlayerFields(target, fields, 0L); }
    public static TitularRequest setPlayerFields(UUID target, PlayerFields fields, long expectedRevision) { return new TitularRequest(Operation.SET_PLAYER_FIELDS,target,expectedRevision,null,null,null,null,fields,null); }
    public static TitularRequest setDisplayMode(DisplayMode mode) { return setDisplayMode(mode, 0L); }
    public static TitularRequest setDisplayMode(DisplayMode mode, long expectedRevision) { return new TitularRequest(Operation.SET_DISPLAY_MODE,null,expectedRevision,null,null,null,null,null,mode); }
    public static TitularRequest refresh() { return refresh(0L); }
    public static TitularRequest refresh(long expectedRevision) { return new TitularRequest(Operation.REFRESH,null,expectedRevision,null,null,null,null,null,null); }
    public static TitularRequest reload() { return reload(0L); }
    public static TitularRequest reload(long expectedRevision) { return new TitularRequest(Operation.RELOAD,null,expectedRevision,null,null,null,null,null,null); }
    public Operation operation(){return operation;} public UUID target(){return target;} public long expectedRevision(){return expectedRevision;} public String id(){return id;} public String titleId(){return titleId;}
    public GroupDefinition group(){return group;} public TitleDefinition title(){return title;} public PlayerFields fields(){return fields;} public DisplayMode mode(){return mode;}
    @Override public boolean equals(Object o){ if (!(o instanceof TitularRequest r)) return false; return operation==r.operation&&Objects.equals(target,r.target)&&expectedRevision==r.expectedRevision&&Objects.equals(id,r.id)&&Objects.equals(titleId,r.titleId)&&Objects.equals(group,r.group)&&Objects.equals(title,r.title)&&Objects.equals(fields,r.fields)&&mode==r.mode; }
    @Override public int hashCode(){ return Objects.hash(operation,target,expectedRevision,id,titleId,group,title,fields,mode); }
}
