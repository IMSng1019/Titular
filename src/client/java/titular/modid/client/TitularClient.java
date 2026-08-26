package titular.modid.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class TitularClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientNetworking.register();
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientNetworking.clearConnectionState());
	}
}
