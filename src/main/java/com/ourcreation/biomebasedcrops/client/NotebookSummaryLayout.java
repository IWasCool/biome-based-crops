package com.ourcreation.biomebasedcrops.client;

import java.util.function.IntUnaryOperator;

import com.ourcreation.biomebasedcrops.GreenhouseRecipes;
import net.minecraft.resources.ResourceLocation;

/** Reuses a page's measured fit while its content, viewport and font resources stay the same. */
final class NotebookSummaryLayout {
    private Context context;
    private Fit fit;

    Fit get(Context requested, IntUnaryOperator measureHeight) {
        if (!requested.equals(context)) {
            int size = 40;
            while (size > 28 && measureHeight.applyAsInt(requested.pageWidth() * 40 / size)
                    > requested.availableHeight() * 40 / size) size--;
            float scale = size / 40.0F;
            int contentHeight = measureHeight.applyAsInt((int) (requested.pageWidth() / scale));
            fit = new Fit(scale, Math.max(0, (int) Math.ceil(contentHeight * scale) - requested.availableHeight()));
            context = requested;
        }
        return fit;
    }

    void clear() { context = null; }

    record Context(ResourceLocation biome, GreenhouseRecipes.Recipe recipe,
                   int pageWidth, int availableHeight, int roomVolume, int floorArea, int referenceVolume,
                   Object language, Object font) { }

    record Fit(float scale, int maxScroll) { }
}
