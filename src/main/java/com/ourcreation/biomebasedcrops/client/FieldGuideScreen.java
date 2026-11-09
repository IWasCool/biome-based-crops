package com.ourcreation.biomebasedcrops.client;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.ourcreation.biomebasedcrops.BiomeBasedCrops;
import com.ourcreation.biomebasedcrops.GreenhouseEnvironment;
import com.ourcreation.biomebasedcrops.GreenhouseRecipes;
import com.ourcreation.biomebasedcrops.GreenhouseSettings;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;

/** A field notebook whose biome recipes use the server's synchronized catalogue. */
public final class FieldGuideScreen extends Screen {
    private static final int INK = 0x493522;
    private static final int FADED_INK = 0x786247;
    private static final int GREEN_INK = 0x416042;
    private static final int PAPER = 0xFFF0DFB7;
    private static final int[] RAGGED = {2, 0, 1, 3, 1, 0, 2, 1, 3, 0, 1};
    private static GreenhouseEnvironment.Inspection lastSurvey;

    private int chapter;
    private int spread;
    private int biomeOffset;
    private int bookX;
    private int bookY;
    private int bookWidth;
    private int bookHeight;
    private int pageWidth;
    private int visibleBiomes;
    private EditBox search;
    private String query = "";
    private List<Holder.Reference<Biome>> biomes = List.of();
    private List<Holder.Reference<Biome>> filtered = List.of();
    private ResourceLocation selectedBiome;
    private ItemStack hoveredSpecimen;
    private ResourceLocation hoveredSpecimenId;
    private ResourceLocation hoveredBiome;
    private BookButton previous;
    private BookButton next;

    public FieldGuideScreen() {
        super(text("title"));
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
        screen.chapter = 4;
        screen.selectedBiome = inspection.targetBiome();
        Minecraft.getInstance().setScreen(screen);
    }

    @Override
    protected void init() {
        bookWidth = Math.min(456, width - 12);
        bookHeight = Math.min(292, height - 12);
        bookX = (width - bookWidth) / 2;
        bookY = (height - bookHeight) / 2;
        pageWidth = (bookWidth - 44) / 2;
        visibleBiomes = Math.max(3, (bookHeight - 112) / 20);
        if (minecraft.level != null) {
            biomes = minecraft.level.registryAccess().registryOrThrow(Registries.BIOME).holders()
                    .sorted(Comparator.comparing(holder -> biomeName(holder.key().location()).getString().toLowerCase(Locale.ROOT)))
                    .toList();
        }
        filterBiomes();
        previous = addRenderableWidget(new BookButton(bookX + bookWidth - 55, bookY + 12, 16, 16,
                Component.literal("<"), button -> turnPage(-1)));
        next = addRenderableWidget(new BookButton(bookX + bookWidth - 36, bookY + 12, 16, 16,
                Component.literal(">"), button -> turnPage(1)));
        int tabWidth = (bookWidth - 30) / 5;
        for (int i = 0; i < 5; i++) {
            final int selected = i;
            BookButton tab = addRenderableWidget(new BookButton(bookX + 15 + i * tabWidth,
                    bookY + bookHeight - 29, tabWidth - 2, 18, text("tab." + i), button -> selectChapter(selected)));
            tab.selected = i == chapter;
            if (i == 4) {
                tab.active = lastSurvey != null;
            }
        }
        search = new EditBox(font, leftX() + 4, contentY() + 17, pageWidth - 8, 14, text("search"));
        search.setMaxLength(128);
        search.setBordered(false);
        search.setTextColor(INK);
        search.setTextColorUneditable(FADED_INK);
        search.setHint(text("search").copy().withStyle(ChatFormatting.DARK_GRAY));
        search.setValue(query);
        search.setResponder(value -> {
            query = value;
            biomeOffset = 0;
            filterBiomes();
        });
        addRenderableWidget(search);
        updateControls();
    }

    private void filterBiomes() {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        filtered = biomes.stream().filter(holder -> holder.key().location().toString().contains(needle)
                || biomeName(holder.key().location()).getString().toLowerCase(Locale.ROOT).contains(needle)).toList();
        biomeOffset = Math.min(biomeOffset, Math.max(0, filtered.size() - visibleBiomes));
        if (!filtered.isEmpty() && filtered.stream().noneMatch(holder -> holder.key().location().equals(selectedBiome))) {
            selectedBiome = filtered.getFirst().key().location();
        }
    }

    private void selectChapter(int selected) {
        chapter = selected;
        spread = 0;
        rebuildWidgets();
    }

    private void updateControls() {
        search.visible = chapter == 3;
        search.active = chapter == 3;
        if (chapter == 3) {
            previous.active = biomeOffset > 0;
            next.active = biomeOffset + visibleBiomes < filtered.size();
        } else {
            previous.active = spread > 0;
            next.active = spread + 1 < spreadCount();
        }
    }

    private void turnPage(int direction) {
        if (chapter == 3) {
            biomeOffset = Math.clamp(biomeOffset + direction * visibleBiomes, 0,
                    Math.max(0, filtered.size() - visibleBiomes));
        } else {
            spread = Math.clamp(spread + direction, 0, Math.max(0, spreadCount() - 1));
        }
        updateControls();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        drawBook(graphics);
        hoveredSpecimen = null;
        hoveredSpecimenId = null;
        hoveredBiome = null;
        if (chapter == 3) {
            renderBiomeCatalogue(graphics, mouseX, mouseY);
        } else {
            renderChapter(graphics);
        }
        updateControls();
        for (var child : children()) {
            if (child instanceof net.minecraft.client.gui.components.Renderable renderable) {
                renderable.render(graphics, mouseX, mouseY, partialTick);
            }
        }
        if (hoveredSpecimen != null) {
            List<Component> tooltip = List.of(hoveredSpecimen.getHoverName(),
                    Component.literal(hoveredSpecimenId.toString()).withStyle(ChatFormatting.GRAY));
            graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        } else if (hoveredBiome != null) {
            graphics.renderComponentTooltip(font, List.of(biomeName(hoveredBiome),
                    Component.literal(hoveredBiome.toString()).withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
        }
    }

    private void drawBook(GuiGraphics g) {
        int x = bookX;
        int y = bookY;
        int w = bookWidth;
        int h = bookHeight;
        g.fill(x + 3, y + 5, x + w + 3, y + h + 4, 0x66000000);
        g.fill(x, y + 3, x + w, y + h - 3, 0xFF422A1D);
        g.fill(x + 3, y, x + w - 3, y + h, 0xFF68452C);
        g.fill(x + 5, y + 4, x + w - 5, y + h - 4, 0xFF875B37);
        for (int px = x + 9; px < x + w - 6; px += 7) {
            g.fill(px, y + 5, px + 3, y + 6, 0xFFC09A62);
            g.fill(px, y + h - 6, px + 3, y + h - 5, 0xFFC09A62);
        }
        drawPaper(g, x + 10, y + 9, (w - 24) / 2, h - 47, false);
        drawPaper(g, x + w / 2 + 2, y + 9, (w - 24) / 2, h - 47, true);
        g.fill(x + w / 2 - 3, y + 11, x + w / 2 - 1, y + h - 39, 0xFFB9A076);
        g.fill(x + w / 2 - 1, y + 10, x + w / 2 + 1, y + h - 38, 0xFFAD8F62);
        g.fill(x + w / 2 + 1, y + 12, x + w / 2 + 3, y + h - 39, 0xFFD3BC8C);
        for (int sy = y + 28; sy < y + h - 50; sy += 29) {
            g.fill(x + w / 2 - 4, sy, x + w / 2 + 5, sy + 1, 0xFF74563C);
            g.fill(x + w / 2 - 4, sy - 1, x + w / 2 - 3, sy + 2, 0xFF493521);
            g.fill(x + w / 2 + 4, sy - 1, x + w / 2 + 5, sy + 2, 0xFF493521);
        }
        g.fill(x + w / 2 + 13, y + h - 42, x + w / 2 + 21, y + h - 28, 0xFF5B7442);
        g.fill(x + w / 2 + 15, y + h - 42, x + w / 2 + 17, y + h - 29, 0xFF78945A);
        drawSprig(g, x + 18, y + 16);
        g.drawString(font, fit(title, Math.max(70, w - 110)), x + 34, y + 16, INK, false);
        String folio = chapter == 3 ? (biomeOffset + 1) + "–" + Math.min(filtered.size(), biomeOffset + visibleBiomes)
                : (spread * 2 + 1) + " · " + (spread * 2 + 2);
        if (chapter == 3 && filtered.isEmpty()) {
            folio = "0";
        }
        g.drawString(font, folio, x + w - 23 - font.width(folio), y + h - 42, FADED_INK, false);
    }

    private void drawPaper(GuiGraphics g, int x, int y, int w, int h, boolean right) {
        g.fill(x + 1, y + 3, x + w, y + h + 2, 0xFFBFA878);
        g.fill(x, y + 1, x + w - 1, y + h, PAPER);
        g.fill(x + 3, y + 3, x + w - 4, y + h - 3, 0xFFF4E5C2);
        for (int py = 0; py < h; py += 5) {
            int rag = RAGGED[(py / 5) % RAGGED.length];
            if (right) {
                g.fill(x + w - 1 - rag, y + py, x + w + 1, y + Math.min(h, py + 3), 0xFF875B37);
                g.fill(x + w - 2 - rag, y + py + 1, x + w - 1 - rag, y + Math.min(h, py + 4), 0xFFD6BC85);
            } else {
                g.fill(x - 1, y + py, x + rag, y + Math.min(h, py + 3), 0xFF875B37);
                g.fill(x + rag, y + py + 1, x + rag + 1, y + Math.min(h, py + 4), 0xFFD6BC85);
            }
        }
        for (int px = 5; px < w - 5; px += 11) {
            int rag = RAGGED[(px / 11) % RAGGED.length];
            g.fill(x + px, y, x + px + 3, y + rag, 0xFFD8BF8B);
            g.fill(x + px + 1, y + h - rag, x + px + 4, y + h, 0xFFCAB080);
        }
        for (int py = 48; py < h - 7; py += 10) {
            g.fill(x + 9, y + py, x + w - 10, y + py + 1, 0x16A88A5D);
        }
        g.fill(x + 9, y + h - 14, x + 16, y + h - 13, 0x26A8854F);
        g.fill(x + 11, y + h - 15, x + 15, y + h - 12, 0x16A8854F);
    }

    private static void drawSprig(GuiGraphics g, int x, int y) {
        g.fill(x + 4, y + 1, x + 5, y + 10, 0xFF687344);
        g.fill(x + 1, y + 2, x + 4, y + 4, 0xFF7C8751);
        g.fill(x + 5, y + 4, x + 8, y + 6, 0xFF566D43);
        g.fill(x + 1, y + 6, x + 4, y + 8, 0xFF566D43);
        g.fill(x + 4, y, x + 6, y + 2, 0xFF9B9754);
    }

    private void renderChapter(GuiGraphics g) {
        List<FormattedCharSequence> lines = chapterLines();
        int capacity = linesPerPage();
        for (int side = 0; side < 2; side++) {
            int px = side == 0 ? leftX() : rightX();
            Component heading = chapter == 4 ? text(side == 0 ? "survey.title" : "survey.checklist")
                    : text("chapter." + chapter + ".title");
            g.drawString(font, fit(heading, pageWidth), px, contentY(), GREEN_INK, false);
            g.fill(px, contentY() + 12, px + pageWidth - 3, contentY() + 13, 0x775A6841);
            int start = (spread * 2 + side) * capacity;
            for (int i = 0; i < capacity && start + i < lines.size(); i++) {
                g.drawString(font, lines.get(start + i), px, contentY() + 20 + i * 10, INK, false);
            }
        }
    }

    private List<FormattedCharSequence> chapterLines() {
        Component body;
        if (chapter == 4 && lastSurvey != null) {
            String target = lastSurvey.targetBiome() == null ? text("survey.none").getString()
                    : biomeName(lastSurvey.targetBiome()).getString() + "\n" + lastSurvey.targetBiome();
            body = text("survey.body", Component.translatable(lastSurvey.status()).withStyle(
                            lastSurvey.valid() ? ChatFormatting.DARK_GREEN : ChatFormatting.DARK_RED), target,
                    lastSurvey.climate().component(), lastSurvey.heat(), lastSurvey.cooling(),
                    lastSurvey.enclosed() ? text("survey.sealed") : text("survey.open"),
                    lastSurvey.glass(), lastSurvey.specimens(), lastSurvey.requiredSpecimens());
        } else {
            body = text("chapter." + chapter + ".body", GreenhouseSettings.radius(), GreenhouseSettings.requiredSpecimens());
        }
        return font.split(body, pageWidth - 2);
    }

    private int linesPerPage() {
        return Math.max(6, (bookHeight - 101) / 10);
    }

    private int spreadCount() {
        return Math.max(1, (chapterLines().size() + linesPerPage() * 2 - 1) / (linesPerPage() * 2));
    }

    private void renderBiomeCatalogue(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, text("catalogue.title"), leftX(), contentY(), GREEN_INK, false);
        g.fill(leftX(), contentY() + 31, leftX() + pageWidth - 2, contentY() + 32, 0x997D7850);
        if (filtered.isEmpty()) {
            drawWrapped(g, text("catalogue.empty"), leftX(), contentY() + 42, pageWidth, 4, FADED_INK);
        }
        for (int i = 0; i < visibleBiomes && biomeOffset + i < filtered.size(); i++) {
            ResourceLocation id = filtered.get(biomeOffset + i).key().location();
            int rowY = contentY() + 37 + i * 20;
            boolean hover = isWithin(mouseX, mouseY, leftX(), rowY, pageWidth - 2, 19);
            if (hover) {
                hoveredBiome = id;
            }
            if (id.equals(selectedBiome)) {
                g.fill(leftX() - 2, rowY - 2, leftX() + pageWidth - 2, rowY + 17, 0x405F7040);
                g.fill(leftX() - 2, rowY - 2, leftX(), rowY + 17, 0xFF657744);
            } else if (hover) {
                g.fill(leftX() - 2, rowY - 2, leftX() + pageWidth - 2, rowY + 17, 0x207D6E4B);
            }
            g.drawString(font, fit(biomeName(id), pageWidth - 7), leftX() + 3, rowY, INK, false);
            g.drawString(font, fit(Component.literal(id.toString()), pageWidth - 7), leftX() + 3, rowY + 9, FADED_INK, false);
        }
        var selected = biomes.stream().filter(holder -> holder.key().location().equals(selectedBiome)).findFirst();
        if (selected.isEmpty()) {
            return;
        }
        var biome = selected.get();
        var recipe = GreenhouseRecipes.forBiome(biome);
        int x = rightX();
        int y = contentY();
        g.drawString(font, fit(biomeName(selectedBiome), pageWidth), x, y, GREEN_INK, false);
        g.fill(x, y + 12, x + pageWidth - 3, y + 13, 0x775A6841);
        g.drawString(font, fit(text("catalogue.climate", recipe.climate().component()), pageWidth), x, y + 20, INK, false);
        g.drawString(font, fit(text("catalogue.samples_short", recipe.count()), pageWidth), x, y + 36, INK, false);
        int iconY = y + 52;
        int columns = Math.max(1, pageWidth / 22);
        int availableRows = Math.max(1, (bookY + bookHeight - 66 - iconY) / 22);
        List<ResourceLocation> specimens = recipe.specimens();
        int limit = Math.min(specimens.size(), columns * availableRows);
        for (int i = 0; i < limit; i++) {
            int ix = x + i % columns * 22;
            int iy = iconY + i / columns * 22;
            g.fill(ix, iy, ix + 19, iy + 19, 0x23A28B5D);
            g.fill(ix, iy, ix + 19, iy + 1, 0x638B7953);
            ItemStack stack = specimenStack(specimens.get(i));
            g.renderItem(stack, ix + 2, iy + 2);
            if (isWithin(mouseX, mouseY, ix, iy, 19, 19)) {
                hoveredSpecimen = stack;
                hoveredSpecimenId = specimens.get(i);
            }
        }
        int noteY = iconY + Math.max(1, (limit + columns - 1) / columns) * 22 + 4;
        drawWrapped(g, text("catalogue.note"), x, noteY, pageWidth, Math.max(0, (bookY + bookHeight - 49 - noteY) / 10), FADED_INK);
    }

    private static ItemStack specimenStack(ResourceLocation id) {
        if (id.equals(ResourceLocation.withDefaultNamespace("water"))) {
            return new ItemStack(Items.WATER_BUCKET);
        }
        if (id.equals(ResourceLocation.withDefaultNamespace("lava"))) {
            return new ItemStack(Items.LAVA_BUCKET);
        }
        return new ItemStack(BuiltInRegistries.BLOCK.get(id).asItem());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (chapter == 3 && button == 0) {
            for (int i = 0; i < visibleBiomes && biomeOffset + i < filtered.size(); i++) {
                int rowY = contentY() + 37 + i * 20;
                if (isWithin(mouseX, mouseY, leftX(), rowY - 2, pageWidth - 2, 19)) {
                    selectedBiome = filtered.get(biomeOffset + i).key().location();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (chapter == 3 && isWithin(mouseX, mouseY, leftX() - 3, contentY() + 32, pageWidth + 3, visibleBiomes * 20 + 6)) {
            biomeOffset = Math.clamp(biomeOffset - (int) Math.signum(scrollY), 0,
                    Math.max(0, filtered.size() - visibleBiomes));
            updateControls();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int leftX() {
        return bookX + 20;
    }

    private int rightX() {
        return bookX + bookWidth / 2 + 13;
    }

    private int contentY() {
        return bookY + 37;
    }

    private int drawWrapped(GuiGraphics g, Component text, int x, int y, int width, int maxLines, int color) {
        List<FormattedCharSequence> lines = font.split(text, width);
        int count = Math.min(maxLines, lines.size());
        for (int i = 0; i < count; i++) {
            g.drawString(font, lines.get(i), x, y + i * 10, color, false);
        }
        return count * 10;
    }

    private String fit(Component text, int maxWidth) {
        String value = text.getString();
        return font.width(value) <= maxWidth ? value : font.plainSubstrByWidth(value, Math.max(0, maxWidth - font.width("…"))) + "…";
    }

    private static Component biomeName(ResourceLocation id) {
        String path = id.getPath().replace('_', ' ');
        String fallback = path.isEmpty() ? id.toString() : Character.toUpperCase(path.charAt(0)) + path.substring(1);
        return Component.translatableWithFallback("biome." + id.getNamespace() + "." + id.getPath().replace('/', '.'), fallback);
    }

    private static boolean isWithin(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private static Component text(String key, Object... values) {
        return Component.translatable(BiomeBasedCrops.MODID + ".guide." + key, values);
    }

    private final class BookButton extends Button {
        private boolean selected;

        private BookButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int base = !active ? 0xFF665039 : selected ? 0xFF566B3D : isHoveredOrFocused() ? 0xFFBD9E64 : 0xFFAD8954;
            g.fill(getX(), getY() + 1, getX() + width, getY() + height - 1, 0xFF483623);
            g.fill(getX() + 1, getY(), getX() + width - 1, getY() + height, 0xFF483623);
            g.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 2, base);
            g.fill(getX() + 2, getY() + 1, getX() + width - 2, getY() + 2, selected ? 0xFF7D905C : 0xFFCAAF77);
            String label = fit(getMessage(), width - 5);
            int color = !active ? 0xA0937A : selected ? 0xF7E7BF : 0x382A1C;
            g.drawString(font, label, getX() + (width - font.width(label)) / 2, getY() + (height - 8) / 2, color, false);
        }
    }
}
