package it.feargames.tileculling.adapter;

import com.comphenix.protocol.events.PacketContainer;
import org.bukkit.Location;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.function.Function;

public interface IAdapter {

	void updateBlockState(Player player, Location location, BlockData blockData);

	void updateBlockData(Player player, Location location, BlockState block);

    void transformPacket(Player player, PacketContainer container, Function<String, Boolean> tileEntityTypeFilter);

}
