package com.bgsoftware.superiorskyblock.world.blocks.stacked.container;

import com.bgsoftware.superiorskyblock.world.chunks.ChunkPosition;
import com.bgsoftware.superiorskyblock.world.blocks.stacked.StackedBlock;
import org.bukkit.Location;

import javax.annotation.Nullable;
import java.util.Map;

public interface StackedBlocksContainer {

    @Nullable
    StackedBlock getStackedBlock(Location location);

    StackedBlock createStackedBlock(Location location);

    StackedBlock removeStackedBlock(Location location);

    Map<Location, StackedBlock> getStackedBlocks(ChunkPosition chunkPosition);

    Map<Location, StackedBlock> getStackedBlocks();

    Map<Location, StackedBlock> removeStackedBlocks(ChunkPosition chunkPosition);

}
