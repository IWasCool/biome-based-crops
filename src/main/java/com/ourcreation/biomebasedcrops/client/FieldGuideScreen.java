package com.ourcreation.biomebasedcrops.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.ourcreation.biomebasedcrops.Config;
import com.ourcreation.biomebasedcrops.CropClimate;
import com.ourcreation.biomebasedcrops.GreenhouseEnvironment;
import com.ourcreation.biomebasedcrops.GreenhouseRecipes;
import com.ourcreation.biomebasedcrops.GreenhouseSettings;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.PageButton;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.fml.ModList;

/** English field index with complete, server-derived instructions for each biome. */
public final class FieldGuideScreen extends Screen {
    private static final int INK = 0x493522;
    private static final int FADED_INK = 0x786247;
    private static final int GREEN_INK = 0x416042;
    private static final ResourceLocation BOOK = ResourceLocation.withDefaultNamespace("textures/gui/book.png");
    private static GreenhouseEnvironment.Inspection lastSurvey;

    private int biomeOffset;
    private int setupPage;
    private int bookX;
    private int bookY;
    private int bookWidth;
    private int bookHeight;
    private int pageWidth;
    private int visibleBiomes;
    private int ticks;
    private EditBox search;
    private String query = "";
    private List<Holder.Reference<Biome>> biomes = List.of();
    private List<Holder.Reference<Biome>> filtered = List.of();
    private ResourceLocation selectedBiome;
    private ResourceLocation hoveredSpecimen;
    private ResourceLocation hoveredBiome;
    private PageButton indexPrevious;
    private PageButton indexNext;
    private PageButton setupPrevious;
    private PageButton setupNext;
    private final List<BiomeLink> biomeLinks = new ArrayList<>();
    private List<List<PageLine>> setupPages = List.of(List.of());
    private boolean surveyOpened;

    public FieldGuideScreen() {
        super(Component.literal("Biome field index"));
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new FieldGuideScreen());
    }

    public static void clearSurvey() {
        lastSurvey = null;
    }

    public static void openSurvey(GreenhouseEnvironment.Inspection inspection) {
        lastSurvey = inspection;
        FieldGuideScreen screen = new FieldGuideScreen();
        screen.selectedBiome = inspection.targetBiome();
        screen.surveyOpened = true;
        Minecraft.getInstance().setScreen(screen);
    }

    @Override
    protected void init() {
        bookWidth = Math.min(456, width - 12);
        bookHeight = Math.min(300, height - 12);
        bookX = (width - bookWidth) / 2;
        bookY = (height - bookHeight) / 2;
        pageWidth = bookWidth / 2 - 48;
        visibleBiomes = Math.max(1, (bookHeight - 96) / 20);
        if (minecraft.level != null) {
            biomes = minecraft.level.registryAccess().registryOrThrow(Registries.BIOME).holders()
                    .sorted(Comparator.comparing(holder -> EnglishGuideText.biomeName(holder.key().location())
                            .getString().toLowerCase(Locale.ROOT))).toList();
        }
        filterBiomes();
        if (surveyOpened && selectedBiome != null) {
            for (int i = 0; i < filtered.size(); i++) {
                if (filtered.get(i).key().location().equals(selectedBiome)) {
                    biomeOffset = i / visibleBiomes * visibleBiomes;
                    break;
                }
            }
        }
        indexPrevious = pageCorner(leftX(), false, "Previous biomes", () -> turnIndex(-1));
        indexNext = pageCorner(leftX() + pageWidth - 23, true, "Next biomes", () -> turnIndex(1));
        setupPrevious = pageCorner(rightX(), false, "Previous setup page", () -> turnSetup(-1));
        setupNext = pageCorner(rightX() + pageWidth - 23, true, "Next setup page", () -> turnSetup(1));
        search = new EditBox(font, leftX(), bookY + 37, pageWidth, 14, Component.literal("Find a biome"));
        search.setMaxLength(128);
        search.setBordered(false);
        search.setTextColor(INK);
        search.setTextColorUneditable(FADED_INK);
        search.setHint(Component.literal("Find a biome...").withStyle(ChatFormatting.DARK_GRAY));
        search.setValue(query);
        search.setResponder(value -> {
            query = value;
            biomeOffset = 0;
            setupPage = 0;
            filterBiomes();
            rebuildSetup();
            updateControls();
        });
        addRenderableWidget(search);
        biomeLinks.clear();
        for (int row = 0; row < visibleBiomes; row++) {
            biomeLinks.add(addRenderableWidget(new BiomeLink(row)));
        }
        rebuildSetup();
        updateControls();
    }

    private PageButton pageCorner(int x, boolean forward, String narration, Runnable action) {
        PageButton corner = addRenderableWidget(new PageButton(x, bookY + bookHeight - 30,
                forward, button -> action.run(), true));
        corner.setMessage(Component.literal(narration));
        return corner;
    }

    private void filterBiomes() {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        filtered = biomes.stream().filter(holder -> holder.key().location().toString().contains(needle)
                || EnglishGuideText.biomeName(holder.key().location()).getString().toLowerCase(Locale.ROOT).contains(needle)).toList();
        biomeOffset = Math.min(biomeOffset, Math.max(0, filtered.size() - visibleBiomes));
        if (!filtered.isEmpty() && filtered.stream().noneMatch(holder -> holder.key().location().equals(selectedBiome))) {
            selectedBiome = filtered.getFirst().key().location();
        }
    }

    private void updateControls() {
        for (BiomeLink link : biomeLinks) {
            int index = biomeOffset + link.row;
            link.visible = index < filtered.size();
            link.active = link.visible;
            if (link.visible) {
                ResourceLocation id = filtered.get(index).key().location();
                link.setMessage(Component.literal(EnglishGuideText.biomeName(id).getString() + ", " + id));
            }
        }
        showCorner(indexPrevious, biomeOffset > 0);
        showCorner(indexNext, biomeOffset + visibleBiomes < filtered.size());
        showCorner(setupPrevious, setupPage > 0);
        showCorner(setupNext, setupPage + 1 < setupPages.size());
    }

    private static void showCorner(PageButton corner, boolean visible) {
        corner.visible = visible;
        corner.active = visible;
    }

    private void turnIndex(int direction) {
        biomeOffset = Math.clamp(biomeOffset + direction * visibleBiomes, 0,
                Math.max(0, filtered.size() - visibleBiomes));
        updateControls();
    }

    private void turnSetup(int direction) {
        setupPage = Math.clamp(setupPage + direction, 0, Math.max(0, setupPages.size() - 1));
        updateControls();
    }

    @Override
    public void tick() {
        if (++ticks % 20 == 0) {
            rebuildSetup();
            updateControls();
        }
    }

    private void rebuildSetup() {
        var selected = biomes.stream().filter(holder -> holder.key().location().equals(selectedBiome)).findFirst();
        List<PageLine> lines = new ArrayList<>();
        if (selected.isEmpty()) {
            addText(lines, "Choose a biome in the index to read its complete greenhouse setup.", false);
        } else {
            var recipe = GreenhouseRecipes.forBiome(selected.get());
            String biome = EnglishGuideText.biomeName(selectedBiome).getString();
            String climate = EnglishGuideText.climateName(recipe.climate()).getString();
            int radius = GreenhouseSettings.radius();
            if (surveyOpened && lastSurvey != null && (lastSurvey.targetBiome() == null || selectedBiome.equals(lastSurvey.targetBiome()))) {
                addSurvey(lines);
            }
            if (!GreenhouseSettings.enabled()) {
                addText(lines, "Greenhouses are disabled by this server. These requirements cannot emulate " + biome + " until the server enables them.", true);
            }
            addText(lines, "Target climate: " + climate + ".", true);
            addText(lines, "Growing radius: " + radius + " blocks. Native blocks: " + recipe.count() + " total, in any mixture.", false);
            addText(lines, "Native choices", true);
            for (int i = 0; i < Math.min(3, recipe.specimens().size()); i++) {
                addSpecimen(lines, recipe.specimens().get(i));
            }
            if (recipe.specimens().size() > 3) {
                addText(lines, "More native choices follow the setup instructions.", false);
            }
            addText(lines, "1. Sample " + biome, true);
            addText(lines, "Probe recipe: glass top-centre; copper, redstone, copper across the middle; a stick bottom-centre. Core recipe: glass, copper, glass across the top; bricks, compass, bricks across the middle; planks, copper, planks across the bottom.", false);
            addText(lines, "Visit " + biome + " (" + selectedBiome + "). Sneak-use the probe there to store its biome sample. Use the sampled probe on your core to bind this biome.", false);
            addText(lines, "2. Enclose the growing room", true);
            addText(lines, "Set the core in the floor near the centre. Keep crops within " + radius + " blocks horizontally and between the core's level and four blocks above it.", false);
            addText(lines, "Seal the floor, walls, and roof; close doors and trapdoors. The walls can be one block beyond the growing radius. Keep the roof at most five blocks above the core. Use at least four glass blocks, including glass overhead.", false);
            addText(lines, "3. Add " + recipe.count() + " native blocks", true);
            addText(lines, "Place " + recipe.count() + " blocks from the listed native choices inside the connected room or in its floor and walls. Any mixture counts; you do not need " + recipe.count() + " of each type. Blocks outside the shell do not count.", false);
            addText(lines, "4. Reach " + climate, true);
            addTemperature(lines, recipe.climate(), radius);
            boolean tanEnabled = GreenhouseSettings.SPEC.isLoaded()
                    ? Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.get() : Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.getDefault();
            if (ModList.get().isLoaded("toughasnails") && tanEnabled) {
                String mode = switch (recipe.climate()) {
                    case ICY, COLD -> "cooling mode";
                    case WARM, HOT -> "heating mode";
                    case NEUTRAL -> "either active heating or cooling mode, or both together";
                };
                addText(lines, "Tough As Nails alternative: an enabled, active thermoregulator in " + mode + " can satisfy this " + climate + " target. Place it within " + Config.THERMOREGULATOR_RADIUS.get() + " blocks horizontally of the core, from one block below to three above. An empty, inactive, or redstone-disabled regulator cannot. The enclosure and native blocks are still required.", false);
            }
            addText(lines, "5. Verify the greenhouse", true);
            addText(lines, "Close the door, then use your probe on the core or a crop. The survey must report Valid, target " + biome + ", and climate " + climate + ". After moving sources or fixing gaps, use the probe again for a fresh reading. Conditions otherwise refresh within one second.", false);
            addText(lines, "A valid " + biome + " greenhouse also satisfies crop rules that require this exact biome or one of its biome tags. It changes growth conditions without changing the world's real biome.", false);
            if (recipe.specimens().size() > 3) {
                addText(lines, "Other native choices", true);
                for (int i = 3; i < recipe.specimens().size(); i++) {
                    addSpecimen(lines, recipe.specimens().get(i));
                }
            }
            if (!surveyOpened && lastSurvey != null && selectedBiome.equals(lastSurvey.targetBiome())) {
                addSurvey(lines);
            }
        }
        List<List<PageLine>> pages = new ArrayList<>();
        List<PageLine> page = new ArrayList<>();
        int used = 0;
        int capacity = Math.max(20, bookHeight - 91);
        for (PageLine line : lines) {
            if (!page.isEmpty() && used + line.height() > capacity) {
                pages.add(List.copyOf(page));
                page.clear();
                used = 0;
            }
            page.add(line);
            used += line.height();
        }
        if (!page.isEmpty() || pages.isEmpty()) {
            pages.add(List.copyOf(page));
        }
        setupPages = List.copyOf(pages);
        setupPage = Math.min(setupPage, setupPages.size() - 1);
    }

    private void addTemperature(List<PageLine> lines, CropClimate target, int radius) {
        BlockPos location = minecraft.player == null ? BlockPos.ZERO : minecraft.player.blockPosition();
        CropClimate local = minecraft.level == null ? CropClimate.NEUTRAL : CropClimate.at(minecraft.level.getBiome(location), location);
        String from = EnglishGuideText.climateName(local).getString();
        String to = EnglishGuideText.climateName(target).getString();
        int steps = target.ordinal() - local.ordinal();
        addText(lines, "Your current location starts at " + from + "; this greenhouse needs " + to + ". The calculation below assumes you build here, with no other temperature sources.", false);
        if (!GreenhouseSettings.ambientEnabled()) {
            addText(lines, "Block heating and cooling are disabled by this server. Fire and ice will not change the climate. "
                    + (steps == 0 ? "The native climate here already matches." : "Build in a naturally " + to + " biome, enable block temperature changes, or use a compatible active thermoregulator if available."), true);
        } else if (steps > 0) {
            addText(lines, "Raise the climate " + steps + " step" + (steps == 1 ? "" : "s") + ": supply " + steps * 8
                    + " net heat points. " + steps + " lit campfire" + (steps == 1 ? "" : "s") + " provide that heat. Fire or lava also supplies 8 points per block; magma supplies 4.", false);
        } else if (steps < 0) {
            int cold = -steps;
            addText(lines, "Lower the climate " + cold + " step" + (cold == 1 ? "" : "s") + ": supply " + cold * 8
                    + " net cooling points. " + cold + " packed-ice or blue-ice block" + (cold == 1 ? "" : "s")
                    + " provide that cooling. Ordinary ice and snow blocks supply 4 points; snow layers supply 1.", false);
        } else {
            addText(lines, "No heating or cooling is needed here. Keep opposing sources balanced so the greenhouse remains " + to + ".", false);
        }
        addText(lines, "Place sources within " + radius + " blocks horizontally of the core, from one block below it to two above. They must be inside the connected room or in its shell. Sources beyond the walls do not count.", false);
        addText(lines, "Eight net points move one climate step. Heat and cooling cancel each other. A sealed greenhouse can move all four steps between Icy and Hot; outdoor plants are limited to two. Unlit campfires give no heat. Keep plants safely away from open fire.", false);
    }

    private void addSurvey(List<PageLine> lines) {
        addText(lines, "Last probe survey", true);
        addText(lines, surveyStatus(lastSurvey.status()), false);
        String target = lastSurvey.targetBiome() == null ? "none sampled" : EnglishGuideText.biomeName(lastSurvey.targetBiome()).getString();
        addText(lines, "Target: " + target + ". Effective climate: " + EnglishGuideText.climateName(lastSurvey.climate()).getString()
                + ". Room: " + (lastSurvey.enclosed() ? "sealed" : "open or incomplete") + ". Glass: " + lastSurvey.glass()
                + "/4. Native blocks: " + lastSurvey.specimens() + "/" + lastSurvey.requiredSpecimens()
                + ". Heat: " + lastSurvey.heat() + ". Cooling: " + lastSurvey.cooling() + ".", false);
        addText(lines, "This is the last scanned reading. Use the probe again after making changes.", false);
    }

    private static String surveyStatus(String key) {
        return switch (key.substring(key.lastIndexOf('.') + 1)) {
            case "valid" -> "Valid greenhouse.";
            case "disabled" -> "Greenhouses are disabled by the server.";
            case "no_core" -> "No greenhouse core is within range.";
            case "unloaded" -> "This area is not loaded.";
            case "outside" -> "The scanned position is outside the greenhouse.";
            case "open" -> "The room is open, incomplete, or too large.";
            case "no_glass" -> "Add four glass blocks and glass overhead.";
            case "unsampled" -> "Bind a sampled biome to the core.";
            case "unknown_biome" -> "The sampled biome is not available in this world.";
            case "missing_specimens" -> "More native specimen blocks are needed.";
            case "wrong_temperature" -> "The greenhouse has the wrong temperature.";
            default -> "Scan the greenhouse again for a fresh reading.";
        };
    }

    private void addText(List<PageLine> lines, String value, boolean heading) {
        Component component = Component.literal(value);
        if (heading) component = component.copy().withStyle(ChatFormatting.DARK_GREEN);
        for (FormattedCharSequence line : font.split(component, pageWidth - 2)) {
            lines.add(new PageLine(line, null, 10));
        }
        lines.add(new PageLine(FormattedCharSequence.EMPTY, null, 5));
    }

    private void addSpecimen(List<PageLine> lines, ResourceLocation id) {
        List<FormattedCharSequence> name = font.split(EnglishGuideText.blockName(id), pageWidth - 24);
        lines.add(new PageLine(name.getFirst(), id, Math.max(20, name.size() * 10 + 2)));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        drawBook(graphics);
        hoveredSpecimen = null;
        hoveredBiome = null;
        if (filtered.isEmpty()) {
            drawWrapped(graphics, Component.literal("No matching biomes. Try a name or mod ID."), leftX(), bookY + 61, pageWidth, 5, FADED_INK);
        }
        renderSetup(graphics, mouseX, mouseY);
        for (var child : children()) {
            if (child instanceof net.minecraft.client.gui.components.Renderable renderable) {
                renderable.render(graphics, mouseX, mouseY, partialTick);
            }
        }
        if (hoveredSpecimen != null) {
            graphics.renderComponentTooltip(font, List.of(EnglishGuideText.blockName(hoveredSpecimen),
                    Component.literal(hoveredSpecimen.toString()).withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
        } else if (hoveredBiome != null) {
            graphics.renderComponentTooltip(font, List.of(EnglishGuideText.biomeName(hoveredBiome),
                    Component.literal(hoveredBiome.toString()).withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
        }
    }

    private void drawBook(GuiGraphics g) {
        int half = bookWidth / 2;
        drawVanillaPage(g, bookX, bookY, half, bookHeight, true);
        drawVanillaPage(g, bookX + half - 1, bookY, bookWidth - half + 1, bookHeight, false);
        for (int side = 0; side < 2; side++) {
            int px = side == 0 ? leftX() : rightX();
            for (int py = bookY + 61; py < bookY + bookHeight - 33; py += 10) {
                g.fill(px - 1, py, px + pageWidth - 2, py + 1, 0x16A88A5D);
            }
        }
        for (int dy : new int[]{29, 77, 119, 167, 211}) {
            if (dy < bookHeight - 15) {
                g.fill(bookX + 12, bookY + dy, bookX + 14, bookY + dy + 3, 0xFFD9CBA3);
                g.fill(bookX + bookWidth - 14, bookY + dy + 9, bookX + bookWidth - 12, bookY + dy + 11, 0xFFE8DAB7);
            }
        }
        g.drawString(font, fit(title, pageWidth), leftX(), bookY + 18, INK, false);
        g.fill(leftX(), bookY + 51, leftX() + pageWidth - 2, bookY + 52, 0x997D7850);
        String range = filtered.isEmpty() ? "0" : (biomeOffset + 1) + "-" + Math.min(filtered.size(), biomeOffset + visibleBiomes) + "/" + filtered.size();
        drawFolio(g, range, leftX());
        drawFolio(g, (setupPage + 1) + "/" + setupPages.size(), rightX());
    }

    private void drawFolio(GuiGraphics g, String value, int x) {
        g.drawString(font, value, x + (pageWidth - font.width(value)) / 2, bookY + bookHeight - 26, FADED_INK, false);
    }

    private static void drawVanillaPage(GuiGraphics g, int x, int y, int width, int height, boolean mirrored) {
        g.blit(BOOK, x, y, width, height, mirrored ? 166.0F : 20.0F, 0.0F,
                mirrored ? -146 : 146, 180, 256, 256);
    }

    private void renderSetup(GuiGraphics g, int mouseX, int mouseY) {
        Component heading = selectedBiome == null ? Component.literal("Greenhouse setup") : EnglishGuideText.biomeName(selectedBiome);
        g.drawString(font, fit(heading, pageWidth), rightX(), bookY + 18, GREEN_INK, false);
        g.drawString(font, "Greenhouse setup", rightX(), bookY + 35, FADED_INK, false);
        g.fill(rightX(), bookY + 47, rightX() + pageWidth - 2, bookY + 48, 0x775A6841);
        int y = bookY + 52;
        for (PageLine line : setupPages.get(setupPage)) {
            if (line.specimen() == null) {
                g.drawString(font, line.text(), rightX(), y, INK, false);
            } else {
                ItemStack stack = specimenStack(line.specimen());
                g.renderItem(stack, rightX(), y + 1);
                List<FormattedCharSequence> name = font.split(EnglishGuideText.blockName(line.specimen()), pageWidth - 24);
                for (int i = 0; i < name.size(); i++) {
                    g.drawString(font, name.get(i), rightX() + 22, y + i * 10 + 1, INK, false);
                }
                if (isWithin(mouseX, mouseY, rightX(), y, pageWidth, line.height())) hoveredSpecimen = line.specimen();
            }
            y += line.height();
        }
    }

    private static ItemStack specimenStack(ResourceLocation id) {
        if (id.equals(ResourceLocation.withDefaultNamespace("water"))) return new ItemStack(Items.WATER_BUCKET);
        if (id.equals(ResourceLocation.withDefaultNamespace("lava"))) return new ItemStack(Items.LAVA_BUCKET);
        return new ItemStack(BuiltInRegistries.BLOCK.get(id).asItem());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isWithin(mouseX, mouseY, leftX() - 3, bookY + 54, pageWidth + 3, visibleBiomes * 20 + 6)) {
            biomeOffset = Math.clamp(biomeOffset - (int) Math.signum(scrollY), 0, Math.max(0, filtered.size() - visibleBiomes));
            updateControls();
            return true;
        }
        if (isWithin(mouseX, mouseY, rightX(), bookY + 52, pageWidth, bookHeight - 90)) {
            turnSetup(-(int) Math.signum(scrollY));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int leftX() { return bookX + 22; }
    private int rightX() { return bookX + bookWidth / 2 + 26; }

    private void drawWrapped(GuiGraphics g, Component component, int x, int y, int width, int maxLines, int color) {
        List<FormattedCharSequence> lines = font.split(component, width);
        for (int i = 0; i < Math.min(maxLines, lines.size()); i++) g.drawString(font, lines.get(i), x, y + i * 10, color, false);
    }

    private String fit(Component component, int maxWidth) {
        String value = component.getString();
        return font.width(value) <= maxWidth ? value : font.plainSubstrByWidth(value, Math.max(0, maxWidth - font.width("..."))) + "...";
    }

    private static boolean isWithin(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private record PageLine(FormattedCharSequence text, ResourceLocation specimen, int height) {}

    private final class BiomeLink extends AbstractButton {
        private final int row;
        private BiomeLink(int row) {
            super(leftX(), bookY + 56 + row * 20, pageWidth - 2, 19, Component.empty());
            this.row = row;
        }
        @Override public void onPress() {
            int index = biomeOffset + row;
            if (index < filtered.size()) {
                selectedBiome = filtered.get(index).key().location();
                setupPage = 0;
                rebuildSetup();
                updateControls();
            }
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
        @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int index = biomeOffset + row;
            if (index >= filtered.size()) return;
            ResourceLocation id = filtered.get(index).key().location();
            if (isHoveredOrFocused()) hoveredBiome = id;
            if (id.equals(selectedBiome)) {
                g.fill(getX() - 2, getY(), getX() + width, getY() + height, 0x305F7040);
                g.fill(getX() - 2, getY(), getX(), getY() + height, 0xFF657744);
            }
            if (isHoveredOrFocused()) g.fill(getX() + 3, getY() + height - 1, getX() + width - 3, getY() + height, 0x775D7044);
            g.drawString(font, fit(EnglishGuideText.biomeName(id), width - 5), getX() + 3, getY() + 2, INK, false);
            g.drawString(font, fit(Component.literal(id.toString()), width - 5), getX() + 3, getY() + 11, FADED_INK, false);
        }
    }
}
