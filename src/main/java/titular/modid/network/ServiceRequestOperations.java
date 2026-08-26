package titular.modid.network;

import titular.modid.model.PermissionLevel;
import titular.modid.service.MutationResult;
import titular.modid.service.TitularService;

import java.util.Objects;
import java.util.UUID;

/** Maps wire operations to server-authoritative service methods. */
public final class ServiceRequestOperations implements TitularRequestHandler.Operations {
    private final TitularService service;

    public ServiceRequestOperations(TitularService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    public TitularService service() { return service; }

    @Override
    public long revision() { return service.snapshot().revision(); }

    @Override
    public MutationResult apply(UUID actor, TitularRequest request) {
        if (actor == null || request == null) return MutationResult.rejected("Malformed Titular request", service.snapshot());
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
            case SET_PLAYER_FIELDS -> {
                TitularRequest.PlayerFields fields = request.fields();
                if (fields == null) yield MutationResult.rejected("Malformed player fields", service.snapshot());
                yield service.setPlayerFields(actor, target, fields.primaryGroup(), fields.extraGroups(),
                        fields.extraTitles(), fields.activeTitle());
            }
            case SET_DISPLAY_MODE -> service.setDisplayMode(actor, request.mode());
            case REFRESH -> MutationResult.accepted(service.snapshot());
            case RELOAD -> service.reload(actor);
        };
    }
}
