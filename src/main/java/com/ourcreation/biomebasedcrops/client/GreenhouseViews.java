package com.ourcreation.biomebasedcrops.client;

import com.ourcreation.biomebasedcrops.GreenhouseNetwork;
import com.ourcreation.biomebasedcrops.GreenhouseRecipes;

public final class GreenhouseViews {
    private GreenhouseViews() {
    }

    public static void open(GreenhouseNetwork.ViewPayload payload) {
        GreenhouseRecipes.acceptServerRecipes(payload.recipes(), payload.referenceVolume());
        if (payload.updateOnly()) {
            FieldGuideScreen.updateRoomContextIfOpen(payload.contextVolume(), payload.contextFloorArea());
        } else {
            FieldGuideScreen.open(payload.contextVolume(), payload.contextFloorArea());
        }
    }
}
