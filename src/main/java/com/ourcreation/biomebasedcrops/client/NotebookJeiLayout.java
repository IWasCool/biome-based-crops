package com.ourcreation.biomebasedcrops.client;

/** Shared book and JEI bounds, independent of client classes so sizing can be checked headlessly. */
public final class NotebookJeiLayout {
    public static final int DEFAULT_DRAWER_HEIGHT = 78;
    public static final int MIN_DRAWER_HEIGHT = 74;
    public static final int MIN_BOOK_HEIGHT = 148;
    private static final int MARGIN = 6, GAP = 6;

    private NotebookJeiLayout() { }

    public static Layout calculate(int screenWidth, int screenHeight, int requestedDrawerHeight) {
        int width = Math.max(0, screenWidth), height = Math.max(0, screenHeight);
        int drawerRoom = Math.max(0, height - MARGIN * 2 - GAP - MIN_BOOK_HEIGHT);
        int drawerHeight = requestedDrawerHeight > 0 && drawerRoom >= MIN_DRAWER_HEIGHT
                ? Math.min(Math.max(MIN_DRAWER_HEIGHT, requestedDrawerHeight), drawerRoom) : 0;
        int gap = drawerHeight == 0 ? 0 : GAP;
        int bookWidth = Math.max(0, Math.min(456, width - MARGIN * 2));
        int bookHeight = Math.max(0, Math.min(300, height - MARGIN * 2 - gap - drawerHeight));
        int bookX = (width - bookWidth) / 2;
        int bookY = (height - bookHeight - gap - drawerHeight) / 2;
        Rect book = new Rect(bookX, bookY, bookWidth, bookHeight);
        if (drawerHeight == 0) return new Layout(book, Rect.EMPTY, Rect.EMPTY);
        int drawerY = book.bottom() + gap;
        int bookmarkWidth = bookWidth / 3;
        return new Layout(book,
                new Rect(bookX, drawerY, bookmarkWidth, drawerHeight),
                new Rect(bookX + bookmarkWidth, drawerY, bookWidth - bookmarkWidth, drawerHeight));
    }

    public record Rect(int x, int y, int width, int height) {
        private static final Rect EMPTY = new Rect(0, 0, 0, 0);
        public int right() { return x + width; }
        public int bottom() { return y + height; }
    }

    public record Layout(Rect book, Rect bookmarks, Rect ingredients) {
        public boolean hasBottomPanels() { return ingredients.height() > 0; }
    }
}
