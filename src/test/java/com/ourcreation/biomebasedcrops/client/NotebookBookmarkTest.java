package com.ourcreation.biomebasedcrops.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class NotebookBookmarkTest {
    private static final ResourceLocation FOREST = ResourceLocation.withDefaultNamespace("forest");
    private static final ResourceLocation DESERT = ResourceLocation.withDefaultNamespace("desert");
    private static final ResourceLocation PLAINS = ResourceLocation.withDefaultNamespace("plains");

    @Test
    void firstOpeningStartsAtTheFirstAvailableBiome() {
        assertEquals(new NotebookBookmark(DESERT, "", 0, 0, 0, 0),
                NotebookBookmark.EMPTY.reconcile(List.of(DESERT, FOREST), 9));
    }

    @Test
    void reopeningPreservesSearchSelectionAndAllScrollPositions() {
        var saved = new NotebookBookmark(FOREST, "forest", 1, 4, 2, 54);

        assertEquals(saved, saved.reconcile(List.of(DESERT, FOREST, PLAINS), 2));
    }

    @Test
    void resizingClampsTheIndexWithoutLosingTheSelectedPage() {
        var saved = new NotebookBookmark(FOREST, "", 20, 4, 2, 54);

        assertEquals(new NotebookBookmark(FOREST, "", 0, 4, 2, 54),
                saved.reconcile(List.of(DESERT, FOREST, PLAINS), 9));
    }

    @Test
    void missingBiomeFallsBackToAnAvailablePageAndResetsItsContentPosition() {
        var saved = new NotebookBookmark(FOREST, "", 20, 4, 2, 54);

        assertEquals(new NotebookBookmark(DESERT, "", 0, 0, 0, 0),
                saved.reconcile(List.of(DESERT, PLAINS), 9));
    }

    @Test
    void emptySearchResultsKeepTheReadersPageAndQuery() {
        var saved = new NotebookBookmark(FOREST, "no such biome", 2, 4, 2, 54);

        assertEquals(new NotebookBookmark(FOREST, "no such biome", 0, 4, 2, 54),
                saved.reconcile(List.of(), 9));
    }
}
