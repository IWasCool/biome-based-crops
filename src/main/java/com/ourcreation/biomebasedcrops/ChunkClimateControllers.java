package com.ourcreation.biomebasedcrops;

import java.util.List;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Candidates belonging to one loaded chunk. Their operation is checked at query time. */
public interface ChunkClimateControllers {
    List<BlockEntity> biomeBasedCrops$climateControllers();
}
