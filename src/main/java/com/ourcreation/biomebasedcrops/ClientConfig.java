package com.ourcreation.biomebasedcrops;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE_TOOLTIPS = BUILDER
            .comment("Adds colored biome climate lines to crop and seed tooltips.")
            .define("enableTooltips", true);

    static final ModConfigSpec SPEC = BUILDER.build();

    private ClientConfig() {
    }
}
