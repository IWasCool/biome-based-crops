package com.ourcreation.biomebasedcrops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ourcreation.biomebasedcrops.client.NotebookJeiLayout;
import org.junit.jupiter.api.Test;

class NotebookJeiLayoutTest {
    @Test
    void disabledDrawerPreservesTheOrdinaryCenteredBook() {
        var layout = NotebookJeiLayout.calculate(640, 400, 0);
        assertEquals(new NotebookJeiLayout.Rect(92, 50, 456, 300), layout.book());
        assertFalse(layout.hasBottomPanels());
    }

    @Test
    void panelsStayBelowTheBookWithinTheViewportAtEverySupportedGuiScale() {
        for (int[] size : new int[][] {{320, 240}, {426, 266}, {640, 400}, {1280, 800}}) {
            for (int requestedHeight : new int[] {78, 132, 456}) {
                var layout = NotebookJeiLayout.calculate(size[0], size[1], requestedHeight);
                assertTrue(layout.hasBottomPanels());
                assertTrue(layout.book().height() >= NotebookJeiLayout.MIN_BOOK_HEIGHT);
                assertTrue(layout.ingredients().height() >= NotebookJeiLayout.MIN_DRAWER_HEIGHT);
                assertEquals(layout.bookmarks().right(), layout.ingredients().x());
                assertEquals(layout.book().x(), layout.bookmarks().x());
                assertEquals(layout.book().right(), layout.ingredients().right());
                assertTrue(layout.book().bottom() + 6 <= layout.ingredients().y());
                assertEquals(layout.ingredients().y(), layout.bookmarks().y());
                assertEquals(layout.ingredients().height(), layout.bookmarks().height());
                for (var area : new NotebookJeiLayout.Rect[] {layout.book(), layout.bookmarks(), layout.ingredients()}) {
                    assertTrue(area.x() >= 0 && area.y() >= 0 && area.width() > 0 && area.height() > 0);
                    assertTrue(area.right() <= size[0] && area.bottom() <= size[1]);
                }
                assertTrue(Math.abs(layout.book().y() - (size[1] - layout.ingredients().bottom())) <= 1);
            }
        }
    }

    @Test
    void anUndersizedViewportDoesNotCreateNegativeOrOverlappingPanels() {
        for (int[] size : new int[][] {{0, 0}, {80, 70}, {320, 200}}) {
            var layout = NotebookJeiLayout.calculate(size[0], size[1], 78);
            assertFalse(layout.hasBottomPanels());
            assertTrue(layout.book().width() >= 0 && layout.book().height() >= 0);
            assertTrue(layout.book().x() >= 0 && layout.book().y() >= 0);
            assertTrue(layout.book().right() <= size[0] && layout.book().bottom() <= size[1]);
        }
    }
}
