package com.ourcreation.biomebasedcrops.client;

import com.ourcreation.biomebasedcrops.GreenhouseNetwork;
import com.ourcreation.biomebasedcrops.GreenhouseRecipes;

public final class GreenhouseViews {
    private GreenhouseViews() {
    }

    public static void open(GreenhouseNetwork.ViewPayload payload) {
        GreenhouseRecipes.acceptServerRecipes(payload.recipes());
        if (payload.inspection() != null) {
            FieldGuideScreen.openSurvey(payload.inspection());
        } else {
            FieldGuideScreen.open();
        }
    }
}
