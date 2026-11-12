package com.ourcreation.biomebasedcrops.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.ourcreation.biomebasedcrops.GreenhouseContent;
import com.ourcreation.biomebasedcrops.GreenhouseRecipes;
import com.ourcreation.biomebasedcrops.client.viewer.GreenhouseViewerBridge;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.PageButton;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;

/** A localized visual field index with one summary for each biome. */
public final class FieldGuideScreen extends Screen {
    private static final int INK = 0x3D2D20;
    private static final int FADED_INK = 0x6C5740;
    private static final ResourceLocation BOOK = ResourceLocation.fromNamespaceAndPath("biome_based_crops", "textures/gui/field_guide_book.png");
    private static NotebookBookmark lastBookmark = NotebookBookmark.EMPTY;
    private int biomeOffset, materialOffset, floorOffset, roomVolume, roomFloorArea;
    private int bookX, bookY, bookWidth, bookHeight, pageWidth, visibleBiomes;
    private NotebookJeiLayout.Layout jeiLayout = NotebookJeiLayout.calculate(0, 0, 0);
    private EditBox search;
    private String query = "";
    private List<Holder.Reference<Biome>> biomes = List.of(), filtered = List.of();
    private List<ItemStack> climateControllers = List.of();
    private ResourceLocation selectedBiome;
    private PageButton indexPrevious, indexNext;
    private final List<IngredientHit> ingredients = new ArrayList<>();
    private final List<BiomeLink> biomeLinks = new ArrayList<>();
    private final NotebookSummaryLayout summaryLayout = new NotebookSummaryLayout();
    private ResourceLocation summaryBiome;
    private float summaryScale = 1;
    private int summaryScroll, maxSummaryScroll;
    private boolean drawingSummary;
    private Rect2i materialStrip, floorStrip;

    public FieldGuideScreen() {
        super(Component.translatable("biome_based_crops.guide.title"));
        restoreBookmark(lastBookmark);
    }

    private NotebookBookmark bookmark() {
        return new NotebookBookmark(selectedBiome, query, biomeOffset, materialOffset, floorOffset, summaryScroll);
    }

    private void restoreBookmark(NotebookBookmark bookmark) {
        selectedBiome = summaryBiome = bookmark.selectedBiome();
        query = bookmark.query();
        biomeOffset = bookmark.biomeOffset();
        materialOffset = bookmark.materialOffset();
        floorOffset = bookmark.floorOffset();
        summaryScroll = bookmark.summaryScroll();
    }

    @Override public void removed() {
        lastBookmark = bookmark();
        super.removed();
    }
    public static void open() { open(0); }
    public static void open(int roomVolume) { open(roomVolume, 0); }
    public static void open(int roomVolume, int floorArea) {
        FieldGuideScreen screen = new FieldGuideScreen();
        screen.roomVolume = Math.max(0, roomVolume);
        screen.roomFloorArea = Math.max(0, floorArea);
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(screen);
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
    }
    public static void updateRoomVolumeIfOpen(int roomVolume) {
        if (Minecraft.getInstance().screen instanceof FieldGuideScreen guide)
            guide.roomVolume = Math.max(0, roomVolume);
    }
    public static void updateRoomContextIfOpen(int roomVolume, int floorArea) {
        if (Minecraft.getInstance().screen instanceof FieldGuideScreen guide) {
            guide.roomVolume = Math.max(0, roomVolume);
            guide.roomFloorArea = Math.max(0, floorArea);
        }
    }
    @Override protected void init() {
        summaryLayout.clear();
        jeiLayout = NotebookJeiLayout.calculate(width, height, GreenhouseViewerBridge.jeiBottomPanelHeight());
        var book = jeiLayout.book();
        bookWidth = book.width();
        bookHeight = book.height();
        bookX = book.x();
        bookY = book.y();
        pageWidth = bookWidth / 2 - 48;
        visibleBiomes = Math.max(1, (bookHeight - 96) / 22);
        if (minecraft.level != null) {
            biomes = minecraft.level.registryAccess().registryOrThrow(Registries.BIOME).holders()
                    .sorted(Comparator.comparing(holder -> EnglishGuideText.biomeName(holder.key().location())
                            .getString().toLowerCase(Locale.ROOT))).toList();
        }
        var controllers = new ArrayList<ItemStack>();
        controllers.add(new ItemStack(GreenhouseContent.AIR_CONDITIONER_ITEM.get()));
        for (String id : List.of("toughasnails:thermoregulator", "northstar:temperature_regulator"))
            BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id)).filter(item -> item != Items.AIR)
                    .ifPresent(item -> controllers.add(new ItemStack(item)));
        climateControllers = List.copyOf(controllers);
        filterBiomes();
        indexPrevious = corner(leftX(), false, "biome_based_crops.guide.previous_biomes", () -> turnIndex(-1));
        indexNext = corner(leftX() + pageWidth - 23, true, "biome_based_crops.guide.next_biomes", () -> turnIndex(1));
        search = new EditBox(font, leftX(), bookY + 37, pageWidth, 14, Component.translatable("biome_based_crops.guide.find_biome"));
        search.setMaxLength(128);
        search.setBordered(false);
        search.setTextColor(INK);
        search.setTextColorUneditable(FADED_INK);
        search.setHint(Component.translatable("biome_based_crops.guide.find_biome_hint").withStyle(ChatFormatting.DARK_GRAY));
        search.setValue(query);
        search.setResponder(value -> {
            query = value;
            biomeOffset = materialOffset = 0;
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
        corner.setMessage(Component.translatable(narration));
        return corner;
    }
    private void filterBiomes() {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        filtered = biomes.stream().filter(holder -> holder.key().location().toString().contains(needle)
                || EnglishGuideText.biomeName(holder.key().location()).getString().toLowerCase(Locale.ROOT).contains(needle)).toList();
        restoreBookmark(bookmark().reconcile(filtered.stream().map(holder -> holder.key().location()).toList(), visibleBiomes));
    }
    private void updateControls() {
        for (BiomeLink link : biomeLinks) {
            int index = biomeOffset + link.row;
            link.visible = link.active = index < filtered.size();
            if (link.visible) link.setMessage(EnglishGuideText.biomeName(filtered.get(index).key().location()));
        }
        show(indexPrevious, biomeOffset > 0);
        show(indexNext, biomeOffset + visibleBiomes < filtered.size());
    }
    private static void show(PageButton button, boolean visible) { button.visible = button.active = visible; }
    private void turnIndex(int direction) {
        biomeOffset = Math.clamp(biomeOffset + direction * visibleBiomes, 0, Math.max(0, filtered.size() - visibleBiomes));
        updateControls();
    }
    private GreenhouseRecipes.Recipe recipe() {
        return biomes.stream().filter(holder -> holder.key().location().equals(selectedBiome)).findFirst()
                .map(GreenhouseRecipes::forBiome).orElse(null);
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        drawBook(g);
        ingredients.clear();
        if (filtered.isEmpty()) wrap(g, Component.translatable("biome_based_crops.guide.no_matches"), leftX(), bookY + 61, pageWidth, 2);
        GreenhouseRecipes.Recipe recipe = recipe();
        if (recipe != null) drawSummary(g, recipe);
        updateControls();
        for (var child : children()) if (child instanceof net.minecraft.client.gui.components.Renderable renderable)
            renderable.render(g, mouseX, mouseY, partialTick);
        ingredientAt(mouseX, mouseY).ifPresent(hit -> {
            if (!GreenhouseViewerBridge.renderTooltip(g, hit.stack(), List.of(), mouseX, mouseY))
                g.renderTooltip(font, hit.stack(), mouseX, mouseY);
        });
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
        g.drawString(font, fit(bold("biome_based_crops.guide.title"), pageWidth), leftX(), bookY + 18, INK, false);
        g.fill(leftX(), bookY + 51, leftX() + pageWidth - 2, bookY + 52, 0x997D7850);
        String range = filtered.isEmpty() ? "0" : (biomeOffset + 1) + "-" + Math.min(filtered.size(), biomeOffset + visibleBiomes) + "/" + filtered.size();
        drawFolio(g, range, leftX());
    }

    private void drawFolio(GuiGraphics g, String value, int x) {
        Component folio = fit(Component.literal(value), pageWidth);
        g.drawString(font, folio, x + (pageWidth - font.width(folio)) / 2, bookY + bookHeight - 26, FADED_INK, false);
    }

    private static void drawVanillaPage(GuiGraphics g, int x, int y, int width, int height, boolean mirrored) {
        g.blit(BOOK, x, y, width, height, mirrored ? 166.0F : 20.0F, 0.0F,
                mirrored ? -146 : 146, 180, 256, 256);
    }

    private void drawSummary(GuiGraphics g, GreenhouseRecipes.Recipe recipe) {
        if (!selectedBiome.equals(summaryBiome)) {
            summaryBiome = selectedBiome;
            floorOffset = summaryScroll = 0;
        }
        // LanguageManager replaces Language on every resource reload, including font-pack reloads.
        // Only the measured fit is cached; drawing and ingredient hitboxes remain live every frame.
        int availableHeight = bookHeight - 44;
        var layout = summaryLayout.get(new NotebookSummaryLayout.Context(selectedBiome, recipe,
                pageWidth, availableHeight, roomVolume, floorArea(), GreenhouseRecipes.referenceVolume(),
                Language.getInstance(), font), contentWidth -> summaryContents(null, recipe, contentWidth));
        summaryScale = layout.scale();
        maxSummaryScroll = layout.maxScroll();
        summaryScroll = Math.clamp(summaryScroll, 0, maxSummaryScroll);
        materialStrip = floorStrip = null;
        drawingSummary = true;
        g.enableScissor(rightX(), bookY + 18, rightX() + pageWidth, bookY + bookHeight - 26);
        g.pose().pushPose();
        g.pose().translate(rightX(), bookY + 18 - summaryScroll, 0);
        g.pose().scale(summaryScale, summaryScale, 1);
        summaryContents(g, recipe, (int) (pageWidth / summaryScale));
        g.pose().popPose();
        g.disableScissor();
        drawingSummary = false;
        if (maxSummaryScroll > 0) drawFolio(g, Component.translatable(summaryScroll < maxSummaryScroll
                ? "biome_based_crops.guide.scroll_more" : "biome_based_crops.guide.scroll_previous").getString(), rightX());
    }

    private int summaryContents(GuiGraphics g, GreenhouseRecipes.Recipe recipe, int width) {
        int y = summaryText(g, EnglishGuideText.biomeName(selectedBiome).copy().withStyle(ChatFormatting.BOLD), 0, 0, width) + 12;
        Component requiredClimate = Component.translatable("biome_based_crops.guide.required_climate",
                EnglishGuideText.requiredClimate(recipe.climate())).withStyle(ChatFormatting.BOLD);
        y += summaryText(g, requiredClimate, 0, y, width) + 5;
        if (g != null) {
            for (int i = 0; i < climateControllers.size(); i++)
                drawIngredient(g, climateControllers.get(i), i * 20, y);
        }
        y += 16 + 8;
        var floor = recipe.floor();
        int area = floorArea();
        y += summaryText(g, bold("biome_based_crops.guide.floor_blocks"), 0, y, width) + 3;
        if (!floor.blocks().isEmpty() && floor.coveragePercent() > 0) {
            int floorColumns = Math.max(1, width / 20);
            floorOffset = Math.clamp(floorOffset, 0, Math.max(0, floor.blocks().size() - floorColumns));
            if (g != null) {
                floorStrip = summaryBounds(0, y, width, 19);
                for (int i = 0; i < Math.min(floorColumns, floor.blocks().size() - floorOffset); i++) {
                    ItemStack stack = specimenStack(floor.blocks().get(floorOffset + i));
                    g.renderItem(stack, i * 20, y);
                    ingredients.add(new IngredientHit(stack.copy(), summaryBounds(i * 20, y, 16, 16), true));
                }
            }
            y += 18;
            if (floor.blocks().size() > floorColumns) y += summaryText(g, Component.translatable("biome_based_crops.guide.scroll_floor"), 0, y, width);
        } else {
            y += summaryText(g, Component.translatable("biome_based_crops.guide.no_floor"), 0, y, width);
        }
        y += 8;

        y += summaryText(g, bold("biome_based_crops.guide.interior_requirements"), 0, y, width) + 3;
        int cellWidth = materialCellWidth(recipe), columns = Math.max(1, width / cellWidth);
        int capacity = columns;
        materialOffset = Math.clamp(materialOffset, 0, Math.max(0, recipe.requirements().size() - capacity));
        int visible = Math.min(capacity, recipe.requirements().size() - materialOffset);
        if (g != null) {
            materialStrip = summaryBounds(0, y, width, 30);
            for (int i = 0; i < visible; i++) {
                var requirement = recipe.requirements().get(materialOffset + i);
                int required = GreenhouseRecipes.scaledCount(requirement, roomVolume, area, GreenhouseRecipes.referenceVolume());
                ItemStack stack = specimenStack(requirement.block());
                stack.setCount(required);
                int cellX = i * cellWidth, itemY = y;
                int itemX = cellX + (cellWidth - 16) / 2;
                drawIngredient(g, stack, itemX, itemY);
                String label = materialLabel(requirement);
                g.drawString(font, label, itemX + 8 - font.width(label) / 2, itemY + 18, INK, false);
            }
        }
        y += 30;
        if (recipe.requirements().size() > capacity) y += summaryText(g, Component.translatable("biome_based_crops.guide.scroll_materials"), 0, y, width);
        y += 8;
        y += summaryText(g, bold("biome_based_crops.guide.setup"), 0, y, width) + 3;
        y += summaryText(g, Component.translatable("biome_based_crops.guide.setup_text"), 0, y, width) + 3;
        return y;
    }

    private int summaryText(GuiGraphics g, Component text, int x, int y, int width) {
        var lines = font.split(text, Math.max(1, width));
        if (g != null) for (int i = 0; i < lines.size(); i++)
            g.drawString(font, lines.get(i), x, y + i * 9, INK, false);
        return lines.size() * 9;
    }
    private Rect2i summaryBounds(int x, int y, int width, int height) {
        int left = rightX() + (int) Math.floor(x * summaryScale);
        int top = bookY + 18 - summaryScroll + (int) Math.floor(y * summaryScale);
        return new Rect2i(left, top, Math.max(1, (int) Math.ceil(width * summaryScale)),
                Math.max(1, (int) Math.ceil(height * summaryScale)));
    }
    private int floorArea() { return roomFloorArea > 0 ? roomFloorArea : 64; }
    private String materialLabel(GreenhouseRecipes.MaterialRequirement material) {
        if (!GreenhouseRecipes.isPercentageBased(material))
            return Component.translatable("biome_based_crops.guide.block_count",
                    GreenhouseRecipes.scaledCount(material, roomVolume, floorArea(), GreenhouseRecipes.referenceVolume())).getString();
        return Math.round(GreenhouseRecipes.materialPercent(material, GreenhouseRecipes.referenceVolume())) + "%";
    }
    private int materialCellWidth(GreenhouseRecipes.Recipe recipe) {
        return Math.max(28, recipe.requirements().stream().mapToInt(material -> font.width(materialLabel(material)) + 2).max().orElse(28));
    }

    private static Component bold(String key) { return Component.translatable(key).withStyle(ChatFormatting.BOLD); }
    private static ItemStack specimenStack(ResourceLocation id) {
        if (id.equals(ResourceLocation.withDefaultNamespace("water"))) return new ItemStack(Items.WATER_BUCKET);
        if (id.equals(ResourceLocation.withDefaultNamespace("lava"))) return new ItemStack(Items.LAVA_BUCKET);
        return new ItemStack(BuiltInRegistries.BLOCK.get(id).asItem());
    }
    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((search == null || !search.isFocused()) && GreenhouseViewerBridge.keyPressed(this, keyCode, scanCode)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Optional<IngredientHit> hit = ingredientAt(mouseX, mouseY);
        if ((button == 0 || button == 1) && hit.isPresent()) {
            blurSearch();
            if (GreenhouseViewerBridge.open(hit.get().stack(), button == 1)) return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (inside(mouseX, mouseY, leftX() - 3, bookY + 54, pageWidth + 3, visibleBiomes * 22 + 6)) {
            biomeOffset = Math.clamp(biomeOffset - (int)Math.signum(scrollY), 0, Math.max(0, filtered.size() - visibleBiomes));
            updateControls(); return true;
        }
        GreenhouseRecipes.Recipe recipe = recipe();
        int width = (int) (pageWidth / summaryScale);
        int columns = recipe == null ? 1 : Math.max(1, width / materialCellWidth(recipe));
        int capacity = columns;
        double movement = scrollY != 0 ? scrollY : scrollX;
        if (recipe != null && inSummary(mouseX, mouseY) && recipe.requirements().size() > capacity
                && contains(materialStrip, mouseX, mouseY)) {
            materialOffset = Math.clamp(materialOffset - (int)Math.signum(movement), 0, recipe.requirements().size() - capacity);
            return true;
        }
        if (recipe != null && inSummary(mouseX, mouseY) && contains(floorStrip, mouseX, mouseY)) {
            int floorColumns = Math.max(1, width / 20);
            if (recipe.floor().blocks().size() > floorColumns) {
                floorOffset = Math.clamp(floorOffset - (int)Math.signum(movement), 0, recipe.floor().blocks().size() - floorColumns);
                return true;
            }
        }
        if (recipe != null && inSummary(mouseX, mouseY) && maxSummaryScroll > 0) {
            summaryScroll = Math.clamp(summaryScroll - (int) (Math.signum(movement) * 18), 0, maxSummaryScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
    private boolean inSummary(double mouseX, double mouseY) {
        return inside(mouseX, mouseY, rightX(), bookY + 18, pageWidth, bookHeight - 44);
    }
    public Rect2i bookBounds() { return new Rect2i(bookX, bookY, bookWidth, bookHeight); }
    public NotebookJeiLayout.Layout jeiLayout() { return jeiLayout; }
    public boolean hasJeiBottomPanels() { return jeiLayout.hasBottomPanels(); }
    public Optional<IngredientHit> ingredientAt(double mouseX, double mouseY) {
        if (!inSummary(mouseX, mouseY)) return Optional.empty();
        return ingredients.stream().filter(hit -> inside(mouseX, mouseY, hit.bounds().getX(), hit.bounds().getY(),
                hit.bounds().getWidth(), hit.bounds().getHeight())).findFirst();
    }
    public Optional<IngredientHit> viewerIngredientAt(double mouseX, double mouseY) {
        return ingredientAt(mouseX, mouseY);
    }
    private void drawIngredient(GuiGraphics g, ItemStack stack, int x, int y) {
        if (stack.isEmpty()) return;
        g.renderItem(stack, x, y);
        ingredients.add(new IngredientHit(stack.copy(), drawingSummary ? summaryBounds(x, y, 16, 16) : new Rect2i(x, y, 16, 16)));
    }
    public record IngredientHit(ItemStack stack, Rect2i bounds, boolean floor) {
        public IngredientHit(ItemStack stack, Rect2i bounds) { this(stack, bounds, false); }
    }
    @Override public void mouseMoved(double mouseX, double mouseY) {
        if (ingredientAt(mouseX, mouseY).isPresent()) blurSearch();
        super.mouseMoved(mouseX, mouseY);
    }
    private void blurSearch() {
        if (search == null) return;
        search.setFocused(false);
        if (getFocused() == search) setFocused(null);
    }
    @Override public boolean isPauseScreen() { return false; }
    private int leftX() { return bookX + 22; }
    private int rightX() { return bookX + bookWidth / 2 + 26; }
    private void wrap(GuiGraphics g, Component value, int x, int y, int width, int maxLines) {
        List<FormattedCharSequence> lines = font.split(value, width);
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
    private static boolean contains(Rect2i bounds, double x, double y) {
        return bounds != null && inside(x, y, bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight());
    }
    private final class BiomeLink extends AbstractButton {
        private final int row;
        private BiomeLink(int row) { super(leftX(),bookY+56+row*22,pageWidth-2,21,Component.empty()); this.row=row; }
        @Override public void onPress() {
            int index=biomeOffset+row;
            if(index<filtered.size()) {
                ResourceLocation biome=filtered.get(index).key().location();
                if (!biome.equals(selectedBiome)) {
                    selectedBiome = summaryBiome = biome;
                    materialOffset = floorOffset = summaryScroll = 0;
                }
                updateControls();
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
            }
            var materials=GreenhouseRecipes.forBiome(holder).specimens();
            if(!materials.isEmpty()) g.renderItem(specimenStack(materials.getFirst()),getX(),getY()+2);
            Component name=EnglishGuideText.biomeName(id);
            if(selected) name=name.copy().withStyle(ChatFormatting.BOLD);
            List<FormattedCharSequence> lines=font.split(name,width-22);
            int lineCount=Math.min(2,lines.size());
            int textY=getY()+(height-(lineCount*10-1))/2;
            for(int i=0;i<lineCount;i++) g.drawString(font,lines.get(i),getX()+21,textY+i*10,INK,false);
        }
    }
}
