package com.ourcreation.biomebasedcrops;

import net.neoforged.fml.event.config.ModConfigEvent;

public final class ConfigEvents {
    private ConfigEvents() {
    }

    public static void onConfigChanged(ModConfigEvent event) {
        if (event.getConfig().getSpec() == Config.SPEC) {
            CropClimateRules.clearCache();
            CropMatcher.clearCaches();
        }
    }
}
