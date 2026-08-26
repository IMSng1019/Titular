package titular.modid.permission;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Creates the optional LuckPerms bridge without hard-loading its API when absent. */
public final class LuckPermsFacadeFactory {
	private static final Logger LOGGER = LoggerFactory.getLogger("titular/luckperms");

	private LuckPermsFacadeFactory() {
	}

	public static LuckPermsFacade create() {
		if (!FabricLoader.getInstance().isModLoaded("luckperms")) {
			return new NoLuckPermsFacade();
		}
		try {
			Class<?> implementation = Class.forName("titular.modid.permission.LuckPermsApiFacade");
			return (LuckPermsFacade) implementation.getDeclaredConstructor().newInstance();
		} catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
			LOGGER.warn("LuckPerms was detected but could not be initialized; using JSON groups", exception);
			return new NoLuckPermsFacade();
		}
	}
}
