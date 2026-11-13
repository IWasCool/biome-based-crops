package com.ourcreation.biomebasedcrops.client.viewer;

import com.ourcreation.biomebasedcrops.client.FieldGuideScreen;
import mezz.jei.api.gui.buttons.IIconButtonController;
import mezz.jei.gui.overlay.ConfigButtonController;
import mezz.jei.gui.overlay.bookmarks.BookmarkButtonController;
import mezz.jei.gui.overlay.bookmarks.history.LookupHistoryButtonController;
import net.minecraft.client.gui.screens.Screen;

/** Only the three JEI utility icons are hidden; no book or panel layout is changed. */
public final class JeiNotebookButtons {
    private JeiNotebookButtons() { }

    public static boolean hidden(Screen screen, IIconButtonController controller) {
        return screen instanceof FieldGuideScreen
                && (controller instanceof ConfigButtonController
                || controller instanceof BookmarkButtonController
                || controller instanceof LookupHistoryButtonController);
    }
}
