package titular.modid.server;

import java.util.UUID;

/** Reports a rejected request and whether the client should refresh its view. */
@FunctionalInterface
public interface ErrorResponder {
    void error(UUID actor, String message, boolean refresh);
}
