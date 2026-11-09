package maihatsuu.hamlet.work;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;

@FunctionalInterface
interface Job {
	/** Performs one work action around the workstation. Returns false if there was nothing to do. */
	boolean run(ServerLevel level, BlockPos site, SimpleContainer stock);
}
