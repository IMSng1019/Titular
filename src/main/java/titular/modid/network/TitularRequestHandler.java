package titular.modid.network;

import titular.modid.model.TitularData;
import titular.modid.service.MutationResult;

import java.util.Objects;
import java.util.UUID;

/** Validates request freshness and coordinates authoritative mutation/broadcast ordering. */
public final class TitularRequestHandler {
    @FunctionalInterface
    public interface SnapshotBroadcaster {
        void broadcast(TitularData snapshot);
    }

    public interface Operations {
        long revision();
        MutationResult apply(UUID actor, TitularRequest request);
    }

    public record Result(boolean success, boolean refresh, String message) {
        public Result {
            message = Objects.requireNonNullElse(message, "");
        }
    }

    private final Operations operations;
    private final SnapshotBroadcaster broadcaster;

    public TitularRequestHandler(Operations operations, SnapshotBroadcaster broadcaster) {
        this.operations = Objects.requireNonNull(operations, "operations");
        this.broadcaster = Objects.requireNonNull(broadcaster, "broadcaster");
    }

    public Result handle(UUID actor, TitularRequest request) {
        if (actor == null || request == null) {
            broadcaster.broadcast(null);
            return new Result(false, true, "Malformed Titular request");
        }
        long expected = request.expectedRevision();
        if (expected < 0 && request.operation() != TitularRequest.Operation.REFRESH) {
            broadcaster.broadcast(null);
            return new Result(false, true, "Titular data revision is required");
        }
        if (expected >= 0 && expected != operations.revision()) {
            broadcaster.broadcast(null);
            return new Result(false, true, "Titular data is stale; refreshed");
        }
        try {
            MutationResult result = operations.apply(actor, request);
            if (result == null) {
                broadcaster.broadcast(null);
                return new Result(false, true, "Titular request was rejected");
            }
            if (!result.success()) {
                broadcaster.broadcast(result.data());
                return new Result(false, true, result.message());
            }
            broadcaster.broadcast(result.data());
            return new Result(true, false, result.message());
        } catch (RuntimeException exception) {
            broadcaster.broadcast(null);
            return new Result(false, true, "Malformed Titular request");
        }
    }
}
