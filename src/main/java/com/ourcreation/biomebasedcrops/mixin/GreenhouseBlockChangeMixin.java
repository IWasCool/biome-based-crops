package com.ourcreation.biomebasedcrops.mixin;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.ourcreation.biomebasedcrops.GreenhouseEnvironment;
import com.ourcreation.biomebasedcrops.GreenhouseRoomScanner;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Level.class)
abstract class GreenhouseBlockChangeMixin {
    @Unique
    private static final ThreadLocal<Map<ServerLevel, LinkedHashMap<Long, BlockState>>> biomeBasedCrops$initialStates = new ThreadLocal<>();
    @Unique
    private static final ThreadLocal<Integer> biomeBasedCrops$mutationDepth = new ThreadLocal<>();
    @Unique
    private static final ThreadLocal<Boolean> biomeBasedCrops$flushing = new ThreadLocal<>();

    @WrapMethod(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z")
    private boolean biomeBasedCrops$changedRoom(BlockPos pos, BlockState state, int flags, int recursion,
            Operation<Boolean> original) {
        Level level = (Level) (Object) this;
        if (level.isClientSide() || !(level instanceof ServerLevel server)
                || !server.getServer().isSameThread() || !GreenhouseRoomScanner.hasTracking(level)
                || level.isOutsideBuildHeight(pos)) {
            return original.call(pos, state, flags, recursion);
        }
        LevelChunk chunk = server.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return original.call(pos, state, flags, recursion);

        Map<ServerLevel, LinkedHashMap<Long, BlockState>> changes = biomeBasedCrops$initialStates.get();
        if (changes == null) {
            changes = new IdentityHashMap<>();
            biomeBasedCrops$initialStates.set(changes);
            biomeBasedCrops$mutationDepth.set(0);
        }
        int depth = biomeBasedCrops$mutationDepth.get();
        biomeBasedCrops$mutationDepth.set(depth + 1);
        Throwable failure = null;
        try {
            LinkedHashMap<Long, BlockState> positions = changes.computeIfAbsent(server, unused -> new LinkedHashMap<>());
            long packed = pos.asLong();
            // A nested write at the same position must keep the outermost state.
            if (!positions.containsKey(packed)) positions.put(packed, chunk.getBlockState(pos));
            return original.call(pos, state, flags, recursion);
        } catch (RuntimeException | Error error) {
            failure = error;
            throw error;
        } finally {
            biomeBasedCrops$mutationDepth.set(depth);
            if (depth == 0 && !Boolean.TRUE.equals(biomeBasedCrops$flushing.get())) {
                try {
                    biomeBasedCrops$flushing.set(true);
                    GreenhouseEnvironment.withoutOverlay(() -> {
                        biomeBasedCrops$flushChanges();
                        return null;
                    });
                } catch (RuntimeException | Error flushFailure) {
                    if (failure == null) throw flushFailure;
                    if (failure != flushFailure) failure.addSuppressed(flushFailure);
                } finally {
                    biomeBasedCrops$initialStates.remove();
                    biomeBasedCrops$mutationDepth.remove();
                    biomeBasedCrops$flushing.remove();
                }
            }
        }
    }

    @Unique
    private static void biomeBasedCrops$flushChanges() {
        Map<ServerLevel, LinkedHashMap<Long, BlockState>> changes = biomeBasedCrops$initialStates.get();
        while (!changes.isEmpty()) {
            var nextLevel = changes.entrySet().iterator().next();
            ServerLevel level = nextLevel.getKey();
            LinkedHashMap<Long, BlockState> positions = nextLevel.getValue();
            if (positions.isEmpty()) {
                changes.remove(level);
                continue;
            }
            var change = positions.pollFirstEntry();
            if (positions.isEmpty()) changes.remove(level);
            // Remove before callbacks: reentrant changes join the pending batch
            // instead of recursively flushing or replaying this initial state.
            if (!GreenhouseRoomScanner.hasTracking(level)) continue;
            BlockPos pos = BlockPos.of(change.getKey());
            if (level.isOutsideBuildHeight(pos)) continue;
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null) continue;
            BlockState after = chunk.getBlockState(pos);
            if (change.getValue() != after) {
                GreenhouseRoomScanner.blockChanged(level, pos, change.getValue(), after);
            }
        }
    }
}
