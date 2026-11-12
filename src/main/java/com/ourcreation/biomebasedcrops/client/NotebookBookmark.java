package com.ourcreation.biomebasedcrops.client;

import java.util.List;

import net.minecraft.resources.ResourceLocation;

/** The reader's place, independent of a screen instance or the current room context. */
record NotebookBookmark(ResourceLocation selectedBiome, String query, int biomeOffset,
                        int materialOffset, int floorOffset, int summaryScroll) {
    static final NotebookBookmark EMPTY = new NotebookBookmark(null, "", 0, 0, 0, 0);

    NotebookBookmark reconcile(List<ResourceLocation> matchingBiomes, int pageSize) {
        int offset = Math.clamp(biomeOffset, 0, Math.max(0, matchingBiomes.size() - Math.max(1, pageSize)));
        if (!matchingBiomes.isEmpty() && (selectedBiome == null || !matchingBiomes.contains(selectedBiome))) {
            return new NotebookBookmark(matchingBiomes.getFirst(), query, offset, 0, 0, 0);
        }
        return new NotebookBookmark(selectedBiome, query, offset, materialOffset, floorOffset, summaryScroll);
    }
}
