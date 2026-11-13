package com.ourcreation.biomebasedcrops.client.viewer;

import com.ourcreation.biomebasedcrops.client.FieldGuideScreen;
import mezz.jei.gui.elements.GuiIconToggleButton;
import mezz.jei.gui.overlay.ConfigButton;
import mezz.jei.gui.overlay.bookmarks.BookmarkButton;
import net.minecraft.client.gui.screens.Screen;

/** Only the three JEI utility icons are hidden; no book or panel layout is changed. */
public final class JeiNotebookButtons {
    private JeiNotebookButtons() { }

    public static boolean hidden(Screen screen, GuiIconToggleButton controller) {
        return screen instanceof FieldGuideScreen
                && (controller instanceof ConfigButton
                || controller instanceof BookmarkButton);
    }
}
