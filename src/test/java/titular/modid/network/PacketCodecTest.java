package titular.modid.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;
import titular.modid.model.DisplayMode;
import titular.modid.model.GroupDefinition;
import titular.modid.model.PermissionLevel;
import titular.modid.model.TitleDefinition;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PacketCodecTest {
    @Test
    void snapshotRoundTripsAndKeepsText() {
        UUID id = UUID.randomUUID();
        OnlineDisplayEntry entry = new OnlineDisplayEntry(id, Text.literal("Alice"),
                new TitleDefinition("vip", Text.literal("["), Text.literal("]")));
        ClientSnapshot snapshot = new ClientSnapshot(12L, DisplayMode.BOTH, null,
                List.of("vip"), List.of(entry),
                PermissionLevel.ADMIN, true, false,
                new ClientSnapshot.ManagementData(List.of("vip-group"), null, null, null));

        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        SnapshotCodec.write(buf, snapshot);
        ClientSnapshot decoded = SnapshotCodec.read(buf);

        assertEquals(snapshot.revision(), decoded.revision());
        assertEquals(snapshot.mode(), decoded.mode());
        assertEquals(snapshot.availableTitleIds(), decoded.availableTitleIds());
        assertEquals(snapshot.onlinePlayers().get(0).playerId(), decoded.onlinePlayers().get(0).playerId());
        assertEquals(Text.literal("Alice"), decoded.onlinePlayers().get(0).rawName());
        assertEquals("vip", decoded.onlinePlayers().get(0).activeTitle().id());
        assertEquals(snapshot.permissionLevel(), decoded.permissionLevel());
    }

    @Test
    void requestsRoundTripWithoutPermissionField() {
        TitularRequest request = TitularRequest.updateGroup(
                new GroupDefinition("vip", "default", List.of("title")));
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        RequestCodec.write(buf, request);
        TitularRequest decoded = RequestCodec.read(buf);
        assertEquals(request, decoded);
        assertFalse(RequestCodec.encodeToString(request).contains("ADMIN"));
    }

    @Test
	void rejectsUnknownOperationAndOversizedText() {
        PacketByteBuf unknown = new PacketByteBuf(Unpooled.buffer());
        unknown.writeVarInt(999);
        assertThrows(IllegalArgumentException.class, () -> RequestCodec.read(unknown));

        PacketByteBuf oversized = new PacketByteBuf(Unpooled.buffer());
        TitleDefinition title = new TitleDefinition("x", Text.literal("a".repeat(20_000)), Text.empty());
        assertThrows(IllegalArgumentException.class, () -> RequestCodec.write(oversized,
                TitularRequest.createTitle(title)));
	}

	@Test
    void rejectsUnexpectedTargetOnSelfOnlyRequest() {
		PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
		buf.writeVarInt(TitularRequest.Operation.ACTIVATE.ordinal());
		buf.writeBoolean(true);
		buf.writeUuid(UUID.randomUUID());
		buf.writeBoolean(true);
		buf.writeString("vip");
		assertThrows(IllegalArgumentException.class, () -> RequestCodec.read(buf));
	}

	@Test
	void rejectsDuplicateSnapshotMapKeys() {
		PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
		buf.writeLong(1L);
		buf.writeVarInt(DisplayMode.PREFIX.ordinal());
		buf.writeBoolean(false);
		SnapshotCodec.writeStrings(buf, List.of());
		buf.writeVarInt(0);
		buf.writeVarInt(PermissionLevel.ADMIN.ordinal());
		buf.writeBoolean(true);
		buf.writeBoolean(false);
		buf.writeBoolean(false);
		buf.writeVarInt(2);
		for (int i = 0; i < 2; i++) {
			SnapshotCodec.writeString(buf, "dup");
			SnapshotCodec.writeText(buf, Text.literal("["));
			SnapshotCodec.writeText(buf, Text.literal("]"));
		}
		assertThrows(IllegalArgumentException.class, () -> SnapshotCodec.read(buf));
	}

	@Test
	void requestsRoundTripExpectedRevision() {
		TitularRequest request = TitularRequest.activate("vip", 42L);
		PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
		RequestCodec.write(buf, request);
		assertEquals(request, RequestCodec.read(buf));
		assertEquals(42L, request.expectedRevision());
    }

    @Test
    void rejectsInvalidRevisionAndTrailingPayload() {
        assertThrows(IllegalArgumentException.class, () -> RequestCodec.write(new PacketByteBuf(Unpooled.buffer()),
                TitularRequest.clear(-2L)));
        assertThrows(IllegalArgumentException.class, () -> RequestCodec.write(new PacketByteBuf(Unpooled.buffer()),
                TitularRequest.clear(-1L)));

        PacketByteBuf trailing = new PacketByteBuf(Unpooled.buffer());
        RequestCodec.write(trailing, TitularRequest.clear(0L));
        trailing.writeByte(1);
        assertThrows(IllegalArgumentException.class, () -> RequestCodec.read(trailing));
    }
}
