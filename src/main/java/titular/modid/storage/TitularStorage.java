package titular.modid.storage;

import titular.modid.model.TitularData;

public interface TitularStorage {
	TitularData load();

	void save(TitularData data);
}
