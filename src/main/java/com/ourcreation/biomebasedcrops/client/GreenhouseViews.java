package com.ourcreation.biomebasedcrops.client;

import com.ourcreation.biomebasedcrops.GreenhouseNetwork;
import com.ourcreation.biomebasedcrops.GreenhouseRecipes;

public final class GreenhouseViews {
    private GreenhouseViews() {
    }

    public static void open(GreenhouseNetwork.ViewPayload payload) {
        GreenhouseRecipes.acceptServerRecipes(payload.recipes(), payload.referenceVolume());
        if (payload.updateOnly()) {
            if (payload.inspection() != null) FieldGuideScreen.updateSurveyIfOpen(payload.inspection());
            else FieldGuideScreen.updateRoomVolumeIfOpen(payload.contextVolume());
            return;
        }
        if (payload.inspection() != null) {
            FieldGuideScreen.openSurvey(payload.inspection());
        } else {
            FieldGuideScreen.open(payload.contextVolume());
        }
    }
}
