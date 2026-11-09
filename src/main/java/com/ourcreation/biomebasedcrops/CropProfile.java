package com.ourcreation.biomebasedcrops;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.biome.Biome;

public record CropProfile(Set<CropClimate> climates, List<BiomeSelector> biomes) {
    public CropProfile {
        Objects.requireNonNull(climates, "climates");
        if (climates.isEmpty()) {
            throw new IllegalArgumentException("A crop profile must allow at least one climate");
        }
        climates = Collections.unmodifiableSet(EnumSet.copyOf(climates));
        biomes = List.copyOf(biomes);
    }

    public CropProfile(Set<CropClimate> climates) {
        this(climates, List.of());
    }

    public boolean hasBiomeRule() {
        return !biomes.isEmpty();
    }

    public boolean allows(Holder<Biome> biome, BlockPos pos) {
        return hasBiomeRule() ? biomes.stream().anyMatch(selector -> selector.matches(biome))
                : allows(CropClimate.at(biome, pos));
    }

    public MutableComponent requirementsComponent() {
        if (!hasBiomeRule()) {
            return climateListComponent();
        }
        MutableComponent line = Component.empty();
        for (int i = 0; i < biomes.size(); i++) {
            if (i > 0) {
                line.append(", ");
            }
            line.append(biomes.get(i).component());
        }
        return line;
    }

    public boolean allows(CropClimate climate) {
        return climates.contains(climate);
    }

    public MutableComponent climateListComponent() {
        MutableComponent line = Component.empty();
        int index = 0;
        for (CropClimate climate : climates) {
            if (index > 0) {
                line.append(Component.literal(", "));
            }
            line.append(climate.component());
            index++;
        }
        return line;
    }
}
