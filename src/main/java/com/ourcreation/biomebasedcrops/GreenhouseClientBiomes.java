package com.ourcreation.biomebasedcrops;

import java.util.UUID;
import javax.annotation.Nullable;
import dev.ryanhcode.sable.companion.SableCompanion;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.joml.Vector3d;

/** The server's nearby room sections, used by client position-based biome queries. */
public final class GreenhouseClientBiomes {
    private static volatile Snapshot room;
    private GreenhouseClientBiomes() { }

    public static void accept(GreenhouseBiomeNetwork.RoomPayload payload) {
        Long2ObjectOpenHashMap<GreenhouseBiomeNetwork.SectionMask> sections = new Long2ObjectOpenHashMap<>();
        for (var section : payload.sections()) sections.put(section.sectionKey(), section);
        room = new Snapshot(payload.dimension(), payload.biome(), Long2ObjectMaps.unmodifiable(sections),
                payload.climate(), payload.oxygenPercent(), payload.shipId());
    }

    public static void clear() { room = null; }

    public static Holder<Biome> biomeAt(Level level, BlockPos pos, Holder<Biome> nativeBiome) {
        Holder<Biome> biome = registeredBiomeAt(level, pos);
        return biome == null ? nativeBiome : biome;
    }

    @Nullable
    static Holder<Biome> registeredBiomeAt(Level level, BlockPos pos) {
        Snapshot snapshot = at(level, pos);
        var biome = snapshot == null ? null : snapshot.biome;
        return biome == null ? null : level.registryAccess().registryOrThrow(Registries.BIOME).getHolder(biome).orElse(null);
    }

    static ResourceLocation biomeIdAt(ResourceLocation dimension, BlockPos pos) {
        Snapshot snapshot = at(dimension, pos);
        return snapshot == null ? null : snapshot.biome;
    }

    public static CropClimate climateAt(Level level, BlockPos pos, CropClimate fallback) {
        Snapshot snapshot = at(level, pos);
        return snapshot == null ? fallback : snapshot.climate;
    }

    static CropClimate climateAt(ResourceLocation dimension, BlockPos pos, CropClimate fallback) {
        Snapshot snapshot = at(dimension, pos);
        return snapshot == null ? fallback : snapshot.climate;
    }

    public static float oxygenAt(Level level, BlockPos pos, float fallback) {
        Snapshot snapshot = at(level, pos);
        return snapshot == null ? fallback : snapshot.oxygen;
    }

    static float oxygenAt(ResourceLocation dimension, BlockPos pos, float fallback) {
        Snapshot snapshot = at(dimension, pos);
        return snapshot == null ? fallback : snapshot.oxygen;
    }

    public static boolean hasOxygen(Level level, BlockPos pos) {
        Snapshot snapshot = at(level, pos);
        return snapshot != null && snapshot.oxygen >= 95;
    }

    static boolean hasOxygen(ResourceLocation dimension, BlockPos pos) {
        Snapshot snapshot = at(dimension, pos);
        return snapshot != null && snapshot.oxygen >= 95;
    }

    private static Snapshot at(ResourceLocation dimension, BlockPos pos) {
        Snapshot snapshot = room;
        // Coordinate-only callers cannot validate a moving structure's current identity or pose.
        if (snapshot == null || snapshot.shipId != null || !snapshot.dimension.equals(dimension)) return null;
        return contains(snapshot, pos) ? snapshot : null;
    }

    private static Snapshot at(Level level, BlockPos pos) {
        Snapshot snapshot = room;
        if (snapshot == null || !snapshot.dimension.equals(level.dimension().location())) return null;
        BlockPos local = localPosition(level, pos, snapshot);
        return local != null && contains(snapshot, local) ? snapshot : null;
    }

    /** Keeps client world-position instruments in the same local room used by the server. */
    static BlockPos localPositionAt(Level level, BlockPos pos) {
        Snapshot snapshot = room;
        if (snapshot == null || !snapshot.dimension.equals(level.dimension().location())) return pos;
        BlockPos local = localPosition(level, pos, snapshot);
        return local != null && contains(snapshot, local) ? local : pos;
    }

    private static BlockPos localPosition(Level level, BlockPos pos, Snapshot snapshot) {
        var companion = SableCompanion.INSTANCE;
        var containing = companion.getContaining(level, pos);
        if (snapshot.shipId == null) return containing == null ? pos : null;
        if (containing != null) return snapshot.shipId.equals(containing.getUniqueId()) ? pos : null;
        // Masks remain in the storage plot. Resolve their owner afresh so a reused plot,
        // an unloaded ship, or a previous connection cannot retain another ship's climate.
        for (long section : snapshot.sections.keySet()) {
            BlockPos anchor = new BlockPos(SectionPos.x(section) << 4, SectionPos.y(section) << 4, SectionPos.z(section) << 4);
            var ship = companion.getContaining(level, anchor);
            if (ship == null || !snapshot.shipId.equals(ship.getUniqueId())) continue;
            Vector3d local = ship.logicalPose().transformPositionInverse(
                    new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
            if (!Double.isFinite(local.x) || !Double.isFinite(local.y) || !Double.isFinite(local.z)) return null;
            BlockPos result = BlockPos.containing(local.x, local.y, local.z);
            var owner = companion.getContaining(level, result);
            return owner != null && snapshot.shipId.equals(owner.getUniqueId()) ? result : null;
        }
        return null;
    }

    private static boolean contains(Snapshot snapshot, BlockPos pos) {
        var mask = snapshot.sections.get(GreenhouseBiomeNetwork.sectionKey(pos));
        return mask != null && mask.contains(pos);
    }

    private record Snapshot(ResourceLocation dimension, ResourceLocation biome,
                            Long2ObjectMap<GreenhouseBiomeNetwork.SectionMask> sections, CropClimate climate, float oxygen,
                            UUID shipId) { }
}
