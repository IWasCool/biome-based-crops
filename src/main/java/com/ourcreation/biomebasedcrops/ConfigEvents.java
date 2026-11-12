package com.ourcreation.biomebasedcrops;

import net.neoforged.fml.event.config.ModConfigEvent;

public final class ConfigEvents {
    private ConfigEvents() {
    }

    public static void onConfigChanged(ModConfigEvent event) {
        if (event.getConfig().getSpec() == GreenhouseSettings.SPEC) {
            if ((event instanceof ModConfigEvent.Loading || event instanceof ModConfigEvent.Reloading)
                    && GreenhouseSettings.migrateConstructionRules()) {
                event.getConfig().getLoadedConfig().save();
            }
            GreenhouseEnvironment.clearCache();
            GreenhouseRecipes.clearCache();
        }
        if (event.getConfig().getSpec() == Config.SPEC) {
            if ((event instanceof ModConfigEvent.Loading || event instanceof ModConfigEvent.Reloading)
                    && Config.migrateClimateRules()) {
                event.getConfig().getLoadedConfig().save();
            }
            GreenhouseEnvironment.invalidateClimate();
            CropClimateRules.clearCache();
            CropMatcher.clearCaches();
        }
    }
}
