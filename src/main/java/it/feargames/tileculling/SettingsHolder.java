package it.feargames.tileculling;

import org.bukkit.configuration.ConfigurationSection;

public class SettingsHolder {

	private int tileRange;

	public void load(ConfigurationSection section) {
		tileRange = section.getInt("tileRange");
	}

	public int getTileRange() {
		return tileRange;
	}
}
