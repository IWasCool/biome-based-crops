package com.ourcreation.biomebasedcrops;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public record CropProfile(Set<CropClimate> climates) {
    public CropProfile {
        Objects.requireNonNull(climates, "climates");
        if (climates.isEmpty()) {
            throw new IllegalArgumentException("A crop profile must allow at least one climate");
        }
        climates = Collections.unmodifiableSet(EnumSet.copyOf(climates));
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
