package titular.modid;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.util.Identifier;

import titular.modid.server.ServerNetworking;
import titular.modid.server.TitularCommand;
import titular.modid.server.TitularServerRuntime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Titular implements ModInitializer {
	public static final String MOD_ID = "titular";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ServerNetworking.register();
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			TitularServerRuntime runtime = new TitularServerRuntime(server);
			runtime.start();
			TitularServerRuntime.setActive(runtime);
			LOGGER.info("Titular server runtime started");
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			TitularServerRuntime runtime = TitularServerRuntime.active();
			if (runtime != null && runtime.server() == server) runtime.stop();
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			TitularServerRuntime runtime = TitularServerRuntime.active();
			if (runtime != null && runtime.server() == server) runtime.playerJoined(handler.player);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			TitularServerRuntime runtime = TitularServerRuntime.active();
			if (runtime != null && runtime.server() == server) runtime.playerDisconnected(handler.player);
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> TitularCommand.register(dispatcher));
	}

	public static Identifier id(String path) {
		return new Identifier(MOD_ID, path);
	}
}
