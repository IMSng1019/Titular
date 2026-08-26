package titular.modid.server;

import titular.modid.model.TitularData;
import titular.modid.network.TitularRequest;
import titular.modid.service.MutationResult;
import titular.modid.service.TitularService;

import java.util.Objects;
import java.util.UUID;

/**
 * Server-only request boundary. It deliberately receives no client supplied
 * permission value; every mutation delegates to the service, which resolves
 * the actor's trusted server-side permission context.
 */
public final class ServerRequestHandler {
    /** Compatibility alias for callers that prefer a nested callback type. */
    @FunctionalInterface
    public interface SnapshotBroadcaster extends titular.modid.server.SnapshotBroadcaster { }

    /** Compatibility alias for callers that prefer a nested callback type. */
    @FunctionalInterface
    public interface ErrorResponder extends titular.modid.server.ErrorResponder { }

    /** Combined callback convenience API for server integrations. */
    public interface Hooks {
        void onSuccess(UUID actor, TitularData data);
        void onError(UUID actor, String message, TitularData data);
    }

    /** Server lifecycle hooks are intentionally injected and Fabric-free. */
    public interface ControlCallbacks {
        MutationResult refresh(UUID actor);
        MutationResult reload(UUID actor);
    }

    public record Result(boolean success, boolean refresh, String message) {
        public Result {
            message = Objects.requireNonNullElse(message, "");
        }
    }

    private final TitularService service;
    private final titular.modid.server.SnapshotBroadcaster broadcaster;
    private final titular.modid.server.ErrorResponder errors;
    private final ControlCallbacks callbacks;
    private final Hooks hooks;

    public ServerRequestHandler(TitularService service,
                                titular.modid.server.SnapshotBroadcaster broadcaster,
                                titular.modid.server.ErrorResponder errors,
                                ControlCallbacks callbacks) {
        this.service = Objects.requireNonNull(service, "service");
        this.broadcaster = Objects.requireNonNull(broadcaster, "broadcaster");
        this.errors = Objects.requireNonNull(errors, "errors");
        this.callbacks = Objects.requireNonNull(callbacks, "callbacks");
        this.hooks = null;
    }

    /**
     * Convenience constructor for integrations that only need success/error
     * hooks. Refresh and reload are intentionally no-op observations here;
     * lifecycle-aware callers should use the callback-injecting constructor.
     */
    public ServerRequestHandler(TitularService service, Hooks hooks) {
        this.service = Objects.requireNonNull(service, "service");
        this.broadcaster = null;
        this.errors = null;
        this.callbacks = defaultCallbacks(service);
        this.hooks = Objects.requireNonNull(hooks, "hooks");
    }

    /** Handles one decoded request on the server thread. */
    public Result handle(UUID actor, TitularRequest request) {
        if (actor == null || request == null) {
            return reject(actor, "Malformed Titular request");
        }

        long expected = request.expectedRevision();
        long actual = service.data().revision();
        if (expected < 0 && request.operation() != TitularRequest.Operation.REFRESH) {
            return reject(actor, "Titular request requires a current snapshot revision");
        }
        if (expected >= 0 && expected != actual) {
            return reject(actor, "Titular data is stale; refreshed");
        }

        try {
            MutationResult result = apply(actor, request);
            if (result == null) return reject(actor, "Titular request was rejected");
            if (!result.success()) return reject(actor, result.message());

            TitularData snapshot = result.data() == null ? service.data() : result.data();
            if (hooks != null) hooks.onSuccess(actor, snapshot);
            else broadcaster.broadcast(snapshot);
            return new Result(true, false, result.message());
        } catch (RuntimeException exception) {
            return reject(actor, messageFor(exception));
        }
    }

    private MutationResult apply(UUID actor, TitularRequest request) {
        UUID target = request.target() == null ? actor : request.target();
        return switch (request.operation()) {
            case ACTIVATE -> service.activateTitle(actor, actor, request.titleId());
            case CLEAR -> service.clearActiveTitle(actor, actor);
            case SET_PRIMARY_GROUP -> service.setPrimaryGroup(actor, target, request.id());
            case CREATE_GROUP -> service.createGroup(actor, request.group());
            case UPDATE_GROUP -> service.updateGroup(actor, request.group());
            case DELETE_GROUP -> service.deleteGroup(actor, request.id());
            case CREATE_TITLE -> service.createTitle(actor, request.title());
            case UPDATE_TITLE -> service.updateTitle(actor, request.title());
            case DELETE_TITLE -> service.deleteTitle(actor, request.id());
            case SET_PLAYER_FIELDS -> applyPlayerFields(actor, target, request);
            case SET_DISPLAY_MODE -> service.setDisplayMode(actor, request.mode());
            case REFRESH -> callbacks.refresh(actor);
            case RELOAD -> callbacks.reload(actor);
        };
    }

    private MutationResult applyPlayerFields(UUID actor, UUID target, TitularRequest request) {
        TitularRequest.PlayerFields fields = request.fields();
        if (fields == null) return MutationResult.rejected("Malformed player fields", service.data());
        return service.setPlayerFields(actor, target, fields.primaryGroup(), fields.extraGroups(),
                fields.extraTitles(), fields.activeTitle());
    }

    private Result reject(UUID actor, String message) {
        String safeMessage = Objects.requireNonNullElse(message, "Titular request was rejected");
        if (hooks != null) {
            hooks.onError(actor, safeMessage, service.data());
        } else {
            // The refresh flag asks the networking layer to send the current
            // authoritative snapshot; failed requests are never success broadcasts.
            errors.error(actor, safeMessage, true);
        }
        return new Result(false, true, safeMessage);
    }

    private static ControlCallbacks defaultCallbacks(TitularService service) {
        return new ControlCallbacks() {
            @Override public MutationResult refresh(UUID actor) {
                return MutationResult.accepted(service.data());
            }

            @Override public MutationResult reload(UUID actor) {
                return MutationResult.accepted(service.data());
            }
        };
    }

    private static String messageFor(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "Titular request failed" : message;
    }
}
