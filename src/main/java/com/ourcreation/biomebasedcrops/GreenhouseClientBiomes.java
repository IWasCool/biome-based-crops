package com.ourcreation.biomebasedcrops;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

/** The server's current room, used by client position-based biome queries. */
public final class GreenhouseClientBiomes {
    private static volatile GreenhouseBiomeNetwork.RoomPayload room;

    private GreenhouseClientBiomes() {
    }

    public static void accept(GreenhouseBiomeNetwork.RoomPayload payload) {
        room = payload;
    }

    public static void clear() {
        room = null;
    }

    public static Holder<Biome> biomeAt(Level level, BlockPos pos, Holder<Biome> nativeBiome) {
        var biome = biomeIdAt(level.dimension().location(), pos);
        if (biome == null) return nativeBiome;
        return level.registryAccess().registryOrThrow(Registries.BIOME)
                .getHolder(biome).map(holder -> (Holder<Biome>) holder).orElse(nativeBiome);
    }

    static ResourceLocation biomeIdAt(ResourceLocation dimension, BlockPos pos) {
        var snapshot = room;
        if (snapshot == null || snapshot.biome() == null
                || !snapshot.dimension().equals(dimension)
                || !snapshot.interior().contains(pos.asLong())) return null;
        return snapshot.biome();
    }
}
