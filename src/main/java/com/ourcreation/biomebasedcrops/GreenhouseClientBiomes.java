package com.ourcreation.biomebasedcrops;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

/** The server's nearby room sections, used by client position-based biome queries. */
public final class GreenhouseClientBiomes {
    private static volatile Snapshot room;
    private GreenhouseClientBiomes() { }

    public static void accept(GreenhouseBiomeNetwork.RoomPayload payload) {
        Long2ObjectOpenHashMap<GreenhouseBiomeNetwork.SectionMask> sections = new Long2ObjectOpenHashMap<>();
        for (var section : payload.sections()) sections.put(section.sectionKey(), section);
        room = new Snapshot(payload.dimension(), payload.biome(), Long2ObjectMaps.unmodifiable(sections));
    }

    public static void clear() { room = null; }

    public static Holder<Biome> biomeAt(Level level, BlockPos pos, Holder<Biome> nativeBiome) {
        var biome = biomeIdAt(level.dimension().location(), pos);
        if (biome == null) return nativeBiome;
        return level.registryAccess().registryOrThrow(Registries.BIOME)
                .getHolder(biome).map(holder -> (Holder<Biome>) holder).orElse(nativeBiome);
    }

    static ResourceLocation biomeIdAt(ResourceLocation dimension, BlockPos pos) {
        Snapshot snapshot = room;
        if (snapshot == null || snapshot.biome == null || !snapshot.dimension.equals(dimension)) return null;
        var mask = snapshot.sections.get(GreenhouseBiomeNetwork.sectionKey(pos));
        return mask != null && mask.contains(pos) ? snapshot.biome : null;
    }

    private record Snapshot(ResourceLocation dimension, ResourceLocation biome,
                            Long2ObjectMap<GreenhouseBiomeNetwork.SectionMask> sections) { }
}
