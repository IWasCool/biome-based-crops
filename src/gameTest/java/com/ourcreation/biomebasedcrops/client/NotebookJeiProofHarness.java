package com.ourcreation.biomebasedcrops.client;

import java.nio.file.Files;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.ourcreation.biomebasedcrops.BiomeBasedCrops;
import com.ourcreation.biomebasedcrops.client.viewer.GreenhouseViewerBridge;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.gui.elements.IconButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Opt-in engine-level UI proof in an isolated preview world; never packaged in the release JAR. */
@EventBusSubscriber(modid = BiomeBasedCrops.MODID, value = Dist.CLIENT)
public final class NotebookJeiProofHarness {
    private static final boolean ENABLED = Boolean.getBoolean("biome_based_crops.notebookJeiProof");
    private static final long SECOND = 1_000_000_000L;
    private static final String[] NAMES = {"book-scale-2", "book-scale-3", "recipes-normal", "book-return", "inventory-restored"};
    private static final JsonArray CAPTURES = new JsonArray();
    private static final AtomicInteger SAVED = new AtomicInteger();
    private static int stage = -1, originalScale = -1, frames;
    private static long started, readySince, stageSince;
    private static boolean advance, done;
    private static Object runtime;
    private static FieldGuideScreen book;

    private NotebookJeiProofHarness() { }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!ENABLED || done) return;
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        if (started == 0) started = now;
        try {
            if (now - started > 180 * SECOND) throw new IllegalStateException("UI proof timed out");
            if (mc.level == null || mc.player == null) return;
            if (!ModList.get().isLoaded("jei")) throw new IllegalStateException("JEI is required for this opt-in proof");
            if (runtime == null) {
                var field = Class.forName("com.ourcreation.biomebasedcrops.client.viewer.GreenhouseJeiPlugin").getDeclaredField("runtime");
                field.setAccessible(true);
                runtime = field.get(null);
                if (runtime == null) return;
            }
            if (readySince == 0) readySince = now;
            if (now - readySince < 5 * SECOND) return;
            if (stage < 0) {
                originalScale = mc.options.guiScale().get();
                stage = 0;
                advance = true;
            }
            if (stage == NAMES.length) {
                if (SAVED.get() == NAMES.length && now - stageSince > SECOND) finish(mc, null);
                return;
            }
            if (!advance) return;
            advance = false;
            switch (stage) {
                case 0 -> { setScale(mc, 2); FieldGuideScreen.open(); book = (FieldGuideScreen) mc.screen; }
                case 1 -> setScale(mc, 3);
                case 2 -> {
                    setScale(mc, 2);
                    if (!GreenhouseViewerBridge.open(new ItemStack(Items.CRAFTING_TABLE), false) || mc.screen == book)
                        throw new IllegalStateException("JEI did not open the crafting table recipe");
                }
                case 3 -> mc.setScreen(book);
                case 4 -> mc.setScreen(new InventoryScreen(mc.player));
                default -> throw new IllegalStateException("Unknown UI proof stage " + stage);
            }
            frames = 0;
            stageSince = now;
        } catch (Throwable error) {
            BiomeBasedCrops.LOGGER.error("[NOTEBOOK_JEI_PROOF] Failed", error);
            finish(mc, error.toString());
        }
    }

    private static void setScale(Minecraft mc, int scale) {
        mc.options.guiScale().set(scale);
        mc.resizeDisplay();
    }

    @SubscribeEvent
    public static void afterFrame(RenderFrameEvent.Post event) {
        if (!ENABLED || done || runtime == null || advance || stage < 0 || stage >= NAMES.length) return;
        if (++frames < 3 || System.nanoTime() - stageSince < 2 * SECOND) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            boolean expectedBook = stage == 0 || stage == 1 || stage == 3;
            if (expectedBook != (mc.screen instanceof FieldGuideScreen)) throw new IllegalStateException("Unexpected proof screen");
            if (mc.screen == null) throw new IllegalStateException("No screen to capture");
            JsonObject sample = new JsonObject();
            sample.addProperty("stage", NAMES[stage]);
            sample.addProperty("screen", mc.screen.getClass().getName());
            sample.addProperty("guiScale", mc.options.guiScale().get());
            sample.addProperty("guiWidth", mc.screen.width);
            sample.addProperty("guiHeight", mc.screen.height);
            sample.addProperty("windowWidth", mc.getWindow().getWidth());
            sample.addProperty("windowHeight", mc.getWindow().getHeight());
            boolean listDisplayed = ((IJeiRuntime) runtime).getIngredientListOverlay().isListDisplayed();
            sample.addProperty("ingredientListDisplayed", listDisplayed);
            if (stage == 4 && !listDisplayed) throw new IllegalStateException("Normal inventory JEI list is not displayed");
            if (expectedBook || stage == 4) {
                JsonArray buttons = new JsonArray();
                IJeiRuntime jei = (IJeiRuntime) runtime;
                checkButton(buttons, jei.getIngredientListOverlay(), "configButton", expectedBook);
                checkButton(buttons, jei.getBookmarkOverlay(), "bookmarkButton", expectedBook);
                checkButton(buttons, jei.getBookmarkOverlay(), "historyButton", expectedBook);
                sample.add("utilityButtons", buttons);
            }
            if (mc.screen instanceof FieldGuideScreen guide) {
                var bounds = guide.bookBounds();
                sample.addProperty("bookX", bounds.getX()); sample.addProperty("bookY", bounds.getY());
                sample.addProperty("bookWidth", bounds.getWidth()); sample.addProperty("bookHeight", bounds.getHeight());
                int expectedWidth = Math.min(456, guide.width - 12);
                int expectedHeight = Math.min(300, guide.height - 12);
                if (bounds.getWidth() != expectedWidth || bounds.getHeight() != expectedHeight
                        || bounds.getX() != (guide.width - expectedWidth) / 2
                        || bounds.getY() != (guide.height - expectedHeight) / 2)
                    throw new IllegalStateException("Notebook no longer has its original centered bounds");
            }
            String filename = "notebook-jei-buttons-" + NAMES[stage] + ".png";
            sample.addProperty("screenshot", "screenshots/" + filename);
            CAPTURES.add(sample);
            Screenshot.grab(mc.gameDirectory, filename, mc.getMainRenderTarget(), message -> SAVED.incrementAndGet());
            stage++;
            stageSince = System.nanoTime();
            advance = stage < NAMES.length;
        } catch (Throwable error) {
            BiomeBasedCrops.LOGGER.error("[NOTEBOOK_JEI_PROOF] Capture failed", error);
            finish(mc, error.toString());
        }
    }

    private static void checkButton(JsonArray states, Object owner, String fieldName, boolean hidden)
            throws ReflectiveOperationException {
        var field = owner.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        IconButton button = (IconButton) field.get(owner);
        boolean visible = button.isVisible();
        boolean hoverable = button.isMouseOver(button.getX() + button.getWidth() / 2.0,
                button.getY() + button.getHeight() / 2.0);
        JsonObject state = new JsonObject();
        state.addProperty("button", fieldName);
        state.addProperty("visible", visible);
        state.addProperty("hoverable", hoverable);
        states.add(state);
        if (visible == hidden || hoverable == hidden)
            throw new IllegalStateException(fieldName + " has incorrect visibility/hover state");
    }

    private static void finish(Minecraft mc, String failure) {
        if (done) return;
        done = true;
        try {
            if (originalScale >= 0) setScale(mc, originalScale);
            mc.setScreen(null);
            var directory = mc.gameDirectory.toPath().toAbsolutePath().normalize();
            if (failure == null) for (String name : NAMES)
                if (!Files.isRegularFile(directory.resolve("screenshots/notebook-jei-buttons-" + name + ".png")))
                    failure = "Missing saved screenshot for " + name;
            JsonObject report = new JsonObject();
            report.addProperty("complete", failure == null);
            report.addProperty("capturedAt", Instant.now().toString());
            report.addProperty("failure", failure);
            report.addProperty("restoredGuiScale", mc.options.guiScale().get());
            report.addProperty("savedScreenshots", SAVED.get());
            report.add("captures", CAPTURES);
            Files.createDirectories(directory);
            Files.writeString(directory.resolve("notebook-jei-buttons-proof.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
            BiomeBasedCrops.LOGGER.info("[NOTEBOOK_JEI_PROOF] {}", failure == null ? "Complete" : failure);
        } catch (Throwable error) {
            BiomeBasedCrops.LOGGER.error("[NOTEBOOK_JEI_PROOF] Could not write proof report", error);
        } finally { mc.stop(); }
    }
}
