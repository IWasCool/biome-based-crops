package com.ourcreation.biomebasedcrops.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntUnaryOperator;

import com.ourcreation.biomebasedcrops.CropClimate;
import com.ourcreation.biomebasedcrops.GreenhouseRecipes;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class NotebookSummaryLayoutTest {
    private static final ResourceLocation FOREST = ResourceLocation.withDefaultNamespace("forest");
    private static final ResourceLocation DESERT = ResourceLocation.withDefaultNamespace("desert");
    private static final GreenhouseRecipes.Recipe RECIPE = new GreenhouseRecipes.Recipe(CropClimate.NEUTRAL,
            List.of(new GreenhouseRecipes.MaterialRequirement(ResourceLocation.withDefaultNamespace("oak_log"), 1)));

    @Test
    void unchangedFramesReuseTheFitWithoutRepeatingFullPageMeasurement() {
        var cache = new NotebookSummaryLayout();
        var context = context(FOREST, RECIPE, 180, 256, 512, 64, 512, new Object(), new Object());
        var measurements = new AtomicInteger();
        IntUnaryOperator measure = width -> { measurements.incrementAndGet(); return 700; };
        var fit = cache.get(context, measure);

        for (int frame = 0; frame < 120; frame++) assertSame(fit, cache.get(context, measure));

        assertEquals(13, measurements.get(), "The minimum-scale page should be measured once, not once per frame");
        assertEquals(0.7F, fit.scale());
        assertEquals(234, fit.maxScroll(), "Overflow remains scrollable at the same minimum scale");
    }

    @Test
    void viewportRecipeRoomAndReloadChangesRemeasureThePage() {
        Object language = new Object(), font = new Object(), reloadedLanguage = new Object();
        var changedRecipe = new GreenhouseRecipes.Recipe(CropClimate.HOT, RECIPE.requirements());
        var contexts = List.of(
                context(FOREST, RECIPE, 180, 256, 512, 64, 512, language, font),
                context(DESERT, RECIPE, 180, 256, 512, 64, 512, language, font),
                context(DESERT, changedRecipe, 180, 256, 512, 64, 512, language, font),
                context(DESERT, changedRecipe, 140, 256, 512, 64, 512, language, font),
                context(DESERT, changedRecipe, 140, 200, 512, 64, 512, language, font),
                context(DESERT, changedRecipe, 140, 200, 1024, 64, 512, language, font),
                context(DESERT, changedRecipe, 140, 200, 1024, 128, 512, language, font),
                context(DESERT, changedRecipe, 140, 200, 1024, 128, 1024, language, font),
                context(DESERT, changedRecipe, 140, 200, 1024, 128, 1024, reloadedLanguage, font),
                context(DESERT, changedRecipe, 140, 200, 1024, 128, 1024, reloadedLanguage, new Object()));
        var cache = new NotebookSummaryLayout();
        var measurements = new AtomicInteger();
        for (var context : contexts) {
            int before = measurements.get();
            var fit = cache.get(context, width -> { measurements.incrementAndGet(); return 100; });
            assertTrue(measurements.get() > before, "A changed layout input must not reuse a stale fit");
            assertEquals(1.0F, fit.scale());
            assertEquals(0, fit.maxScroll());
        }
    }

    @Test
    void screenReinitializationClearsTheCachedMeasurement() {
        var cache = new NotebookSummaryLayout();
        var context = context(FOREST, RECIPE, 180, 256, 512, 64, 512, new Object(), new Object());
        assertEquals(0, cache.get(context, width -> 100).maxScroll());

        cache.clear();

        assertTrue(cache.get(context, width -> 700).maxScroll() > 0);
    }

    private static NotebookSummaryLayout.Context context(ResourceLocation biome, GreenhouseRecipes.Recipe recipe,
            int width, int height, int volume, int floor, int reference, Object language, Object font) {
        return new NotebookSummaryLayout.Context(biome, recipe, width, height, volume, floor, reference, language, font);
    }
}
