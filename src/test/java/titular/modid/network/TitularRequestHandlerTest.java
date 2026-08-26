package titular.modid.network;

import org.junit.jupiter.api.Test;
import titular.modid.model.TitularData;
import titular.modid.service.MutationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TitularRequestHandlerTest {
    private static final UUID ACTOR = UUID.randomUUID();

    @Test
    void rejectsForgedAdminRequestWithoutClientPermissionContext() {
        List<String> events = new ArrayList<>();
        TitularRequestHandler.Operations operations = new FakeOperations(4, events, false);
        TitularRequestHandler handler = new TitularRequestHandler(operations, snapshot -> events.add("broadcast"));

        TitularRequestHandler.Result result = handler.handle(ACTOR,
                TitularRequest.setPrimaryGroup(ACTOR, "staff", 4));

        assertFalse(result.success());
        assertEquals(List.of("apply", "broadcast"), events);
    }

    @Test
    void rejectsStaleRevisionBeforeMutationAndRequestsRefresh() {
        List<String> events = new ArrayList<>();
        TitularRequestHandler.Operations operations = new FakeOperations(5, events, true);
        TitularRequestHandler handler = new TitularRequestHandler(operations, snapshot -> events.add("broadcast"));

        TitularRequestHandler.Result result = handler.handle(ACTOR, TitularRequest.clear(4));

        assertFalse(result.success());
        assertTrue(result.refresh());
        assertEquals(List.of("broadcast"), events);
    }

    @Test
    void broadcastsOnlyAfterSuccessfulSave() {
        List<String> events = new ArrayList<>();
        TitularRequestHandler.Operations operations = new FakeOperations(0, events, true);
        TitularRequestHandler handler = new TitularRequestHandler(operations,
                snapshot -> events.add("broadcast:" + snapshot.revision()));

        TitularRequestHandler.Result result = handler.handle(ACTOR, TitularRequest.clear(0));

        assertTrue(result.success());
        assertEquals(List.of("apply", "broadcast:1"), events);
    }

    private static final class FakeOperations implements TitularRequestHandler.Operations {
        private long revision;
        private final List<String> events;
        private final boolean accepted;

        private FakeOperations(long revision, List<String> events, boolean accepted) {
            this.revision = revision;
            this.events = events;
            this.accepted = accepted;
        }

        @Override
        public long revision() { return revision; }

        @Override
        public MutationResult apply(UUID actor, TitularRequest request) {
            events.add("apply");
            if (!accepted) return MutationResult.rejected("forbidden", new TitularData());
            TitularData data = new TitularData(null, null, null, null, revision + 1);
            revision = data.revision();
            return MutationResult.accepted(data);
        }
    }
}
