package com.ourcreation.biomebasedcrops;

import net.neoforged.fml.event.config.ModConfigEvent;

public final class ConfigEvents {
    private ConfigEvents() {
    }

    public static void onConfigChanged(ModConfigEvent event) {
        if (event.getConfig().getSpec() == GreenhouseSettings.SPEC) {
            GreenhouseEnvironment.clearCache();
            GreenhouseRecipes.clearCache();
        }
        if (event.getConfig().getSpec() == Config.SPEC) {
            GreenhouseEnvironment.invalidateClimate();
            CropClimateRules.clearCache();
            CropMatcher.clearCaches();
        }
    }
}
