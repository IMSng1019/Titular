package titular.modid.server;

import titular.modid.model.TitularData;

/** Sends the latest authoritative snapshot to clients after a request. */
@FunctionalInterface
public interface SnapshotBroadcaster {
    void broadcast(TitularData snapshot);
}
