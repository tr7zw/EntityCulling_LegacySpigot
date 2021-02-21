package it.feargames.tileculling;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

public class HiddenTileRegistry {

	private final Logger logger;

	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	private final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();
	private final ReentrantReadWriteLock.ReadLock readLock = lock.readLock();

	private Material[] hiddenMaterials;
	private String[] hiddenNamespaces;

	public HiddenTileRegistry(Logger logger) {
		this.logger = logger;
	}

	public void load(ConfigurationSection config) {
		Set<Material> materials = new HashSet<>();
		for (String materialName : config.getStringList("materials")) {
			Material material = Material.getMaterial(materialName);
			if (material == null || !material.isBlock()) {
				logger.warning("Material " + materialName + " is invalid!");
				continue;
			}
			materials.add(material);
		}
		for (String tagName : config.getStringList("tags")) {
			Tag<?> tag;
			try {
				tag = (Tag<?>) Tag.class.getDeclaredField(tagName).get(null);
			} catch (NoSuchFieldException e) {
				logger.warning("Material tag " + tagName + " is invalid!");
				continue;
			} catch (IllegalAccessException e) {
				throw new RuntimeException(e);
			}
			if (!tag.getClass().getSimpleName().equals("CraftBlockTag")) {
				logger.warning("Material tag " + tagName + " is not a block tag!");
				continue;
			}
			//noinspection unchecked
			Tag<Material> blockTag = (Tag<Material>) tag;
			materials.addAll(blockTag.getValues());
		}
		load(materials);
	}

	public void load(Collection<Material> materials) {
		try {
			writeLock.lock();
			hiddenMaterials = materials.toArray(new Material[0]);
			hiddenNamespaces = materials.stream().map(material -> material.getKey().toString()).toArray(String[]::new);
		} finally {
			writeLock.unlock();
		}
		logger.info("Loaded " + materials.size() + " hidden tile types");
	}

	public boolean shouldHide(String namespacedKey) {
		try {
			readLock.lock();
			for (String current : hiddenNamespaces) {
				if (current.equals(namespacedKey)) {
					return true;
				}
			}
			return true;
		} finally {
			readLock.unlock();
		}
	}

	public boolean shouldHide(Material material) {
		try {
			readLock.lock();
			for (Material current : hiddenMaterials) {
				if (current == material) {
					return true;
				}
			}
			return true;
		} finally {
			readLock.unlock();
		}
	}

	public boolean shouldHide(BlockState state) {
		return shouldHide(state.getType());
	}
}
