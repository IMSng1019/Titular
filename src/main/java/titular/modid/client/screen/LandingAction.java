package titular.modid.client.screen;

import titular.modid.client.TitularScreenState;
import titular.modid.model.PermissionLevel;

import java.util.Arrays;
import java.util.List;

/**
 * Actions shown on the first page of the Titular screen.
 *
 * <p>This type intentionally lives in the common source set even though it is
 * used by the client screen.  The projection is pure data and is also useful
 * to tests without loading Minecraft's client classes.</p>
 */
public enum LandingAction {
    SWITCH_TITLE(TitularScreenState.Page.TITLE_SWITCH,
            "titular.screen.action.switch_title", PermissionLevel.PLAYER),
    SELECT_PRIMARY_GROUP(TitularScreenState.Page.PRIMARY_GROUP,
            "titular.screen.action.select_title", PermissionLevel.ADMIN),
    MANAGE_TITLES(TitularScreenState.Page.MANAGEMENT,
            "titular.screen.action.manage_titles", PermissionLevel.SUPERADMIN),
    LANGUAGE(TitularScreenState.Page.LANGUAGE,
            "titular.screen.action.language", PermissionLevel.PLAYER);

    /** Compatibility aliases matching the wording used by the UI spec. */
    public static final LandingAction SELECT_TITLE = SELECT_PRIMARY_GROUP;
    public static final LandingAction SET_TITLE = MANAGE_TITLES;

    private final TitularScreenState.Page page;
    private final String labelKey;
    private final PermissionLevel requiredPermission;

    LandingAction(TitularScreenState.Page page, String labelKey, PermissionLevel requiredPermission) {
        this.page = page;
        this.labelKey = labelKey;
        this.requiredPermission = requiredPermission;
    }

    public TitularScreenState.Page page() { return page; }

    public String labelKey() { return labelKey; }

    public PermissionLevel requiredPermission() { return requiredPermission; }

    public boolean visibleFor(PermissionLevel level) {
        PermissionLevel effective = level == null ? PermissionLevel.PLAYER : level;
        return effective.includes(requiredPermission);
    }

    /** Returns actions in stable landing-page order for the supplied role. */
    public static List<LandingAction> visible(PermissionLevel level) {
        return Arrays.stream(values()).filter(action -> action.visibleFor(level)).toList();
    }

    /** Alias used by callers that prefer a permission-oriented name. */
    public static List<LandingAction> forPermission(PermissionLevel level) {
        return visible(level);
    }
}
