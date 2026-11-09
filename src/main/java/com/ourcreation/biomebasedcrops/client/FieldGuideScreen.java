package com.ourcreation.biomebasedcrops.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.ourcreation.biomebasedcrops.Config;
import com.ourcreation.biomebasedcrops.CropClimate;
import com.ourcreation.biomebasedcrops.GreenhouseContent;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.fml.ModList;

/** A visual English field index. Every requirement stays on its selected biome's cards. */
public final class FieldGuideScreen extends Screen {
    private static final int INK = 0x3D2D20;
    private static final int FADED_INK = 0x6C5740;
    private static final ResourceLocation BOOK = ResourceLocation.withDefaultNamespace("textures/gui/book.png");
    private static GreenhouseEnvironment.Inspection lastSurvey;
    private int biomeOffset, setupPage, materialOffset, selectedMaterial;
    private int bookX, bookY, bookWidth, bookHeight, pageWidth, visibleBiomes;
    private EditBox search;
    private String query = "";
    private List<Holder.Reference<Biome>> biomes = List.of(), filtered = List.of();
    private ResourceLocation selectedBiome;
    private PageButton indexPrevious, indexNext, setupPrevious, setupNext;
    private final List<BiomeLink> biomeLinks = new ArrayList<>();
    private boolean surveyOpened;

    public FieldGuideScreen() { super(Component.literal("Biome field index")); }
    public static void open() { Minecraft.getInstance().setScreen(new FieldGuideScreen()); }
    public static void clearSurvey() { lastSurvey = null; }
    public static void openSurvey(GreenhouseEnvironment.Inspection inspection) {
        lastSurvey = inspection;
        FieldGuideScreen screen = new FieldGuideScreen();
        screen.selectedBiome = inspection.targetBiome();
        screen.surveyOpened = true;
        Minecraft.getInstance().setScreen(screen);
    }

    @Override protected void init() {
        bookWidth = Math.min(456, width - 12);
        bookHeight = Math.min(300, height - 12);
        bookX = (width - bookWidth) / 2;
        bookY = (height - bookHeight) / 2;
        pageWidth = bookWidth / 2 - 48;
        visibleBiomes = Math.max(1, (bookHeight - 96) / 22);
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
        indexPrevious = corner(leftX(), false, "Previous biomes", () -> turnIndex(-1));
        indexNext = corner(leftX() + pageWidth - 23, true, "Next biomes", () -> turnIndex(1));
        setupPrevious = corner(rightX(), false, "Previous diagram", () -> turnSetup(-1));
        setupNext = corner(rightX() + pageWidth - 23, true, "Next diagram", () -> turnSetup(1));
        search = new EditBox(font, leftX(), bookY + 37, pageWidth, 14, Component.literal("Find a biome"));
        search.setMaxLength(128);
        search.setBordered(false);
        search.setTextColor(INK);
        search.setTextColorUneditable(FADED_INK);
        search.setHint(Component.literal("Find a biome...").withStyle(ChatFormatting.DARK_GRAY));
        search.setValue(query);
        search.setResponder(value -> {
            query = value;
            biomeOffset = setupPage = materialOffset = selectedMaterial = 0;
            filterBiomes();
            updateControls();
        });
        addRenderableWidget(search);
        biomeLinks.clear();
        for (int row = 0; row < visibleBiomes; row++) biomeLinks.add(addRenderableWidget(new BiomeLink(row)));
        updateControls();
    }

    private PageButton corner(int x, boolean forward, String narration, Runnable action) {
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
        if (!filtered.isEmpty() && filtered.stream().noneMatch(holder -> holder.key().location().equals(selectedBiome)))
            selectedBiome = filtered.getFirst().key().location();
    }
    private void updateControls() {
        for (BiomeLink link : biomeLinks) {
            int index = biomeOffset + link.row;
            link.visible = link.active = index < filtered.size();
            if (link.visible) link.setMessage(EnglishGuideText.biomeName(filtered.get(index).key().location()));
        }
        show(indexPrevious, biomeOffset > 0);
        show(indexNext, biomeOffset + visibleBiomes < filtered.size());
        setupPage = Math.min(setupPage, cardCount() - 1);
        show(setupPrevious, setupPage > 0);
        show(setupNext, setupPage + 1 < cardCount());
    }
    private static void show(PageButton button, boolean visible) { button.visible = button.active = visible; }
    private void turnIndex(int direction) {
        biomeOffset = Math.clamp(biomeOffset + direction * visibleBiomes, 0, Math.max(0, filtered.size() - visibleBiomes));
        updateControls();
    }
    private void turnSetup(int direction) {
        setupPage = Math.clamp(setupPage + direction, 0, cardCount() - 1);
        updateControls();
    }
    private boolean hasSurvey() {
        return surveyOpened && lastSurvey != null && (lastSurvey.targetBiome() == null || selectedBiome != null && selectedBiome.equals(lastSurvey.targetBiome()));
    }
    private int cardCount() { return hasSurvey() ? 4 : 3; }
    private int card() { return setupPage - (hasSurvey() ? 1 : 0); }
    private GreenhouseRecipes.Recipe recipe() {
        return biomes.stream().filter(holder -> holder.key().location().equals(selectedBiome)).findFirst()
                .map(GreenhouseRecipes::forBiome).orElse(null);
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        drawBook(g);
        if (filtered.isEmpty()) wrap(g, "No matches", leftX(), bookY + 61, pageWidth, 2);
        GreenhouseRecipes.Recipe recipe = recipe();
        if (recipe != null) {
            Component name = EnglishGuideText.biomeName(selectedBiome).copy().withStyle(ChatFormatting.BOLD);
            g.drawString(font, fit(name, pageWidth), rightX(), bookY + 18, INK, false);
            String label = card() < 0 ? "Probe reading" : switch (card()) {
                case 0 -> "Materials";
                case 1 -> "Build the room";
                default -> "Craft & sample";
            };
            g.drawString(font, fit(bold(label), pageWidth), rightX(), bookY + 35, INK, false);
            if (card() < 0) drawSurvey(g, recipe);
            else if (card() == 0) drawMaterials(g, recipe);
            else if (card() == 1) drawRoom(g, recipe);
            else drawCrafting(g);
        }
        updateControls();
        for (var child : children()) if (child instanceof net.minecraft.client.gui.components.Renderable renderable)
            renderable.render(g, mouseX, mouseY, partialTick);
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
        g.drawString(font, fit(bold("Biome field index"), pageWidth), leftX(), bookY + 18, INK, false);
        g.fill(leftX(), bookY + 51, leftX() + pageWidth - 2, bookY + 52, 0x997D7850);
        String range = filtered.isEmpty() ? "0" : (biomeOffset + 1) + "-" + Math.min(filtered.size(), biomeOffset + visibleBiomes) + "/" + filtered.size();
        drawFolio(g, range, leftX());
        drawFolio(g, (setupPage + 1) + "/" + cardCount(), rightX());
    }

    private void drawFolio(GuiGraphics g, String value, int x) {
        g.drawString(font, value, x + (pageWidth - font.width(value)) / 2, bookY + bookHeight - 26, FADED_INK, false);
    }

    private static void drawVanillaPage(GuiGraphics g, int x, int y, int width, int height, boolean mirrored) {
        g.blit(BOOK, x, y, width, height, mirrored ? 166.0F : 20.0F, 0.0F,
                mirrored ? -146 : 146, 180, 256, 256);
    }

    private void drawMaterials(GuiGraphics g, GreenhouseRecipes.Recipe recipe) {
        int x = rightX(), y = bookY + 53;
        g.drawString(font, bold(recipe.count() + " total; any mix"), x, y, INK, false);
        int columns = Math.max(1, pageWidth / 18);
        boolean scroll = scrollMaterials(recipe);
        int rows = scroll ? 1 : Math.max(1, (recipe.specimens().size() + columns - 1) / columns);
        materialOffset = scroll ? Math.clamp(materialOffset, 0, Math.max(0, recipe.specimens().size() - columns)) : 0;
        int visible = Math.min(recipe.specimens().size() - materialOffset, columns * rows);
        for (int i = 0; i < visible; i++) {
            int at = materialOffset + i, ix = x + i % columns * 18, iy = y + 14 + i / columns * 18;
            if (at == selectedMaterial) g.fill(ix - 1, iy - 1, ix + 17, iy + 17, 0x30A58B5B);
            g.renderItem(specimenStack(recipe.specimens().get(at)), ix, iy);
        }
        selectedMaterial = Math.clamp(selectedMaterial, 0, Math.max(0, recipe.specimens().size() - 1));
        int caption = y + 16 + rows * 18;
        if (!recipe.specimens().isEmpty()) {
            Component name = EnglishGuideText.blockName(recipe.specimens().get(selectedMaterial));
            g.drawString(font, fit(name, pageWidth - (scroll ? 18 : 0)), x, caption, FADED_INK, false);
            if (scroll) g.drawString(font, "<>", x + pageWidth - 14, caption, INK, false);
        }
        CropClimate local = nativeClimate();
        int thermalY = caption + 15;
        String from = EnglishGuideText.climateName(local).getString(), to = EnglishGuideText.climateName(recipe.climate()).getString();
        g.drawString(font, fit(Component.literal(from + " -> " + to), pageWidth), x, thermalY, INK, false);
        int cell = pageWidth / 5;
        String[] names = cell >= 30 ? new String[]{"Icy", "Cold", "N", "Warm", "Hot"} : new String[]{"I", "C", "N", "W", "H"};
        int[] paper = {0xFFCCDCE1, 0xFFD5DDDA, 0xFFE2D7BD, 0xFFE5CAA7, 0xFFE3B593};
        for (int i = 0; i < 5; i++) {
            int cx = x + cell * i, cy = thermalY + 12;
            g.fill(cx, cy, cx + cell - 1, cy + 14, paper[i]);
            if (i == recipe.climate().ordinal()) border(g, cx, cy, cell - 1, 14);
            g.drawString(font, names[i], cx + (cell - font.width(names[i])) / 2, cy + 3, INK, false);
            if (i == local.ordinal()) g.fill(cx + cell / 2 - 1, cy + 15, cx + cell / 2 + 2, cy + 17, 0xFF3D2D20);
        }
        int steps = recipe.climate().ordinal() - local.ordinal(), sourceY = thermalY + 34;
        if (!GreenhouseSettings.ambientEnabled()) {
            itemLabel(g, Items.BARRIER, "Blocks disabled", x, sourceY);
        } else if (steps == 0) {
            itemLabel(g, Items.CLOCK, "No heat / cold", x, sourceY);
        } else {
            Item item = steps > 0 ? Items.CAMPFIRE : Items.PACKED_ICE;
            g.renderItem(new ItemStack(item), x, sourceY);
            g.drawString(font, fit(bold("x" + Math.abs(steps) + " -> " + to), pageWidth - 21), x + 21, sourceY + 2, INK, false);
            g.drawString(font, Math.abs(steps) * 8 + (steps > 0 ? " net heat" : " net cooling"), x + 21, sourceY + 12, FADED_INK, false);
        }
        int noteY = sourceY + 25;
        if (!GreenhouseSettings.enabled()) line(g, "No greenhouses", x, noteY);
        else if (!GreenhouseSettings.ambientEnabled()) line(g, tanActive() ? "Native or active TAN" : "Native climate only", x, noteY);
        else if (tanActive()) wrap(g, "TAN: " + switch (recipe.climate()) {
            case ICY, COLD -> "cooling (on)";
            case WARM, HOT -> "heating (on)";
            case NEUTRAL -> "either (on)";
        }, x, noteY, pageWidth, 2);
        else wrap(g, "Heat cancels cold.", x, noteY, pageWidth, 1);
    }

    private void drawRoom(GuiGraphics g, GreenhouseRecipes.Recipe recipe) {
        int x = rightX(), y = bookY + 53, start = x + (pageWidth - 80) / 2;
        Item nativeBlock = recipe.specimens().isEmpty() ? Items.MOSS_BLOCK : specimenStack(recipe.specimens().getFirst()).getItem();
        for (int col = 0; col < 5; col++) {
            g.renderItem(new ItemStack(Items.GLASS), start + col * 16, y);
            Item floor = col == 2 ? GreenhouseContent.CORE_ITEM.get() : col == 1 || col == 3 ? Items.FARMLAND : Items.DIRT;
            g.renderItem(new ItemStack(floor), start + col * 16, y + 48);
        }
        for (int row = 1; row < 3; row++) {
            g.renderItem(new ItemStack(Items.GLASS), start, y + row * 16);
            g.renderItem(new ItemStack(Items.GLASS), start + 64, y + row * 16);
        }
        g.renderItem(new ItemStack(nativeBlock), start + 32, y + 16);
        g.renderItem(new ItemStack(Items.WHEAT), start + 16, y + 32);
        g.renderItem(new ItemStack(Items.WHEAT), start + 48, y + 32);
        int climateSteps = recipe.climate().ordinal() - nativeClimate().ordinal();
        if (climateSteps != 0 && GreenhouseSettings.ambientEnabled()) {
            ItemStack source = new ItemStack(climateSteps < 0 ? Items.PACKED_ICE : Items.CAMPFIRE, Math.abs(climateSteps));
            g.renderItem(source, start + 32, y + 32);
            g.renderItemDecorations(font, source, start + 32, y + 32);
        }
        int labelY = y + 68;
        int radius = GreenhouseSettings.radius();
        line(g, "Radius " + radius + "; roof +5", x, labelY);
        line(g, "4+ glass incl. roof", x, labelY + 11);
        line(g, "Seal all 6 sides", x, labelY + 22);
        line(g, "Sources inside r=" + radius, x, labelY + 33);
        line(g, "Heat/ice: Y-1..+2", x, labelY + 44);
        line(g, "Close door; scan", x, labelY + 55);
    }

    private void drawCrafting(GuiGraphics g) {
        int x = rightX(), y = bookY + 53, grid = 48, gap = Math.max(6, pageWidth - grid * 2);
        int right = x + grid + gap;
        g.drawString(font, bold("Probe"), x + 8, y, INK, false);
        g.drawString(font, bold("Core"), right + 9, y, INK, false);
        Item air = Items.AIR, glass = Items.GLASS, copper = Items.COPPER_INGOT;
        drawRecipe(g, x, y + 12, new Item[]{air,glass,air,copper,Items.REDSTONE,copper,air,Items.STICK,air});
        drawRecipe(g, right, y + 12, new Item[]{glass,copper,glass,Items.BRICKS,Items.COMPASS,Items.BRICKS,Items.OAK_PLANKS,copper,Items.OAK_PLANKS});
        g.renderItem(new ItemStack(GreenhouseContent.PROBE.get()), x + 16, y + 65);
        g.renderItem(new ItemStack(GreenhouseContent.CORE_ITEM.get()), right + 16, y + 65);
        line(g, "Visit biome first", x, y + 85);
        int column = pageWidth / 3;
        Item[] icons = {GreenhouseContent.PROBE.get(), GreenhouseContent.CORE_ITEM.get(), GreenhouseContent.PROBE.get()};
        String[] labels = {"Sneak", "Bind", "Scan"};
        for (int i = 0; i < 3; i++) {
            int ix = x + i * column + (column - 16) / 2;
            g.renderItem(new ItemStack(icons[i]), ix, y + 99);
            g.drawString(font, labels[i], x + i * column + (column - font.width(labels[i])) / 2, y + 118, INK, false);
            if (i < 2) g.drawString(font, ">", x + (i + 1) * column - 3, y + 103, INK, false);
        }
    }

    private void drawRecipe(GuiGraphics g, int x, int y, Item[] items) {
        for (int i = 0; i < 9; i++) {
            int ix = x + i % 3 * 16, iy = y + i / 3 * 16;
            g.fill(ix, iy, ix + 16, iy + 16, 0x22A18C69);
            if (items[i] != Items.AIR) g.renderItem(new ItemStack(items[i]), ix, iy);
        }
    }

    private void drawSurvey(GuiGraphics g, GreenhouseRecipes.Recipe recipe) {
        int x = rightX(), y = bookY + 53;
        line(g, lastSurvey.valid() ? "[OK] VALID" : "[ ] NEEDS WORK", x, y);
        String issue = switch (lastSurvey.status().substring(lastSurvey.status().lastIndexOf('.') + 1)) {
            case "valid" -> "Ready to grow";
            case "no_core" -> "No core nearby";
            case "open" -> "Room open / too large";
            case "no_glass" -> "Add glass + roof glass";
            case "missing_specimens" -> "Add native blocks";
            case "wrong_temperature" -> "Adjust heat / cooling";
            case "unsampled" -> "Sample then bind core";
            case "disabled" -> "Disabled by server";
            case "outside" -> "Outside the room";
            default -> "Check the core";
        };
        line(g, issue, x, y + 13);
        itemLabel(g, GreenhouseContent.CORE_ITEM.get(), lastSurvey.enclosed() ? "[OK] Sealed" : "[ ] Seal room", x, y + 29);
        itemLabel(g, Items.GLASS, lastSurvey.glass() + "/4 glass + roof", x, y + 49);
        Item material = recipe.specimens().isEmpty() ? Items.MOSS_BLOCK : specimenStack(recipe.specimens().getFirst()).getItem();
        itemLabel(g, material, lastSurvey.specimens() + "/" + lastSurvey.requiredSpecimens() + " native", x, y + 69);
        itemLabel(g, lastSurvey.cooling() > lastSurvey.heat() ? Items.PACKED_ICE : Items.CAMPFIRE,
                EnglishGuideText.climateName(lastSurvey.climate()).getString(), x, y + 89);
        line(g, "Heat " + lastSurvey.heat() + " / cold " + lastSurvey.cooling(), x, y + 111);
        line(g, "Scan again to refresh", x, y + 123);
    }

    private CropClimate nativeClimate() {
        BlockPos pos = minecraft.player == null ? BlockPos.ZERO : minecraft.player.blockPosition();
        return minecraft.level == null ? CropClimate.NEUTRAL : CropClimate.at(minecraft.level.getBiome(pos), pos);
    }
    private boolean tanActive() {
        return ModList.get().isLoaded("toughasnails") && (GreenhouseSettings.SPEC.isLoaded()
                ? Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.get() : Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.getDefault());
    }
    private static Component bold(String value) { return Component.literal(value).withStyle(ChatFormatting.BOLD); }
    private void line(GuiGraphics g, String value, int x, int y) { g.drawString(font, fit(Component.literal(value), pageWidth), x, y, INK, false); }
    private void itemLabel(GuiGraphics g, Item item, String value, int x, int y) {
        g.renderItem(new ItemStack(item), x, y);
        g.drawString(font, fit(Component.literal(value), pageWidth - 22), x + 22, y + 4, INK, false);
    }
    private static void border(GuiGraphics g, int x, int y, int width, int height) {
        g.fill(x,y,x+width,y+1,0xFF3D2D20); g.fill(x,y+height-1,x+width,y+height,0xFF3D2D20);
        g.fill(x,y,x+1,y+height,0xFF3D2D20); g.fill(x+width-1,y,x+width,y+height,0xFF3D2D20);
    }
    private static ItemStack specimenStack(ResourceLocation id) {
        if (id.equals(ResourceLocation.withDefaultNamespace("water"))) return new ItemStack(Items.WATER_BUCKET);
        if (id.equals(ResourceLocation.withDefaultNamespace("lava"))) return new ItemStack(Items.LAVA_BUCKET);
        return new ItemStack(BuiltInRegistries.BLOCK.get(id).asItem());
    }
    private boolean scrollMaterials(GreenhouseRecipes.Recipe recipe) {
        int columns = Math.max(1, pageWidth / 18);
        return recipe.specimens().size() > columns * (pageWidth < 140 ? 1 : 2);
    }
    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        GreenhouseRecipes.Recipe recipe = recipe();
        if (button == 0 && card() == 0 && recipe != null) {
            int columns = Math.max(1, pageWidth / 18);
            int rows = scrollMaterials(recipe) ? 1 : Math.max(1, (recipe.specimens().size() + columns - 1) / columns);
            int y = bookY + 67;
            if (inside(mouseX, mouseY, rightX(), y, columns * 18, rows * 18)) {
                int index = materialOffset + (int)(mouseY - y) / 18 * columns + (int)(mouseX - rightX()) / 18;
                if (index < recipe.specimens().size()) selectedMaterial = index;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (inside(mouseX, mouseY, leftX() - 3, bookY + 54, pageWidth + 3, visibleBiomes * 22 + 6)) {
            biomeOffset = Math.clamp(biomeOffset - (int)Math.signum(scrollY), 0, Math.max(0, filtered.size() - visibleBiomes));
            updateControls(); return true;
        }
        GreenhouseRecipes.Recipe recipe = recipe();
        int columns = Math.max(1, pageWidth / 18);
        if (card() == 0 && recipe != null && scrollMaterials(recipe)
                && inside(mouseX, mouseY, rightX(), bookY + 65, pageWidth, 38)) {
            materialOffset = Math.clamp(materialOffset - (int)Math.signum(scrollY), 0, recipe.specimens().size() - columns);
            selectedMaterial = materialOffset; return true;
        }
        if (inside(mouseX, mouseY, rightX(), bookY + 52, pageWidth, bookHeight - 90)) {
            turnSetup(-(int)Math.signum(scrollY)); return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
    @Override public boolean isPauseScreen() { return false; }
    private int leftX() { return bookX + 22; }
    private int rightX() { return bookX + bookWidth / 2 + 26; }
    private void wrap(GuiGraphics g, String value, int x, int y, int width, int maxLines) {
        List<FormattedCharSequence> lines = font.split(Component.literal(value), width);
        for (int i = 0; i < Math.min(maxLines, lines.size()); i++) g.drawString(font, lines.get(i), x, y + i * 10, INK, false);
    }
    private Component fit(Component value, int maxWidth) {
        String text = value.getString();
        if (font.width(value) <= maxWidth) return value;
        while (!text.isEmpty()) {
            Component clipped = Component.literal(text + "...").withStyle(value.getStyle());
            if (font.width(clipped) <= maxWidth) return clipped;
            text = text.substring(0, text.length() - 1);
        }
        return Component.empty();
    }
    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
    private final class BiomeLink extends AbstractButton {
        private final int row;
        private BiomeLink(int row) { super(leftX(),bookY+56+row*22,pageWidth-2,21,Component.empty()); this.row=row; }
        @Override public void onPress() {
            int index=biomeOffset+row;
            if(index<filtered.size()) {
                selectedBiome=filtered.get(index).key().location(); setupPage=materialOffset=selectedMaterial=0; updateControls();
            }
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
        @Override protected void renderWidget(GuiGraphics g,int mouseX,int mouseY,float partialTick) {
            int index=biomeOffset+row;
            if(index>=filtered.size()) return;
            var holder=filtered.get(index);
            ResourceLocation id=holder.key().location();
            boolean selected=id.equals(selectedBiome);
            if(selected) {
                g.fill(getX()-2,getY(),getX()+width,getY()+height,0x26A68A58);
                g.fill(getX()-2,getY(),getX(),getY()+height,0xFF6D5033);
            }
            if(isFocused()) g.fill(getX()+20,getY()+height-1,getX()+width-2,getY()+height,0x994B3825);
            var materials=GreenhouseRecipes.forBiome(holder).specimens();
            if(!materials.isEmpty()) g.renderItem(specimenStack(materials.getFirst()),getX(),getY()+2);
            Component name=EnglishGuideText.biomeName(id);
            if(selected) name=name.copy().withStyle(ChatFormatting.BOLD);
            List<FormattedCharSequence> lines=font.split(name,width-22);
            for(int i=0;i<Math.min(2,lines.size());i++) g.drawString(font,lines.get(i),getX()+21,getY()+1+i*10,INK,false);
        }
    }
}
