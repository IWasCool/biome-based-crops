package com.ourcreation.biomebasedcrops.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.ourcreation.biomebasedcrops.Config;
import com.ourcreation.biomebasedcrops.GreenhouseContent;
import com.ourcreation.biomebasedcrops.GreenhouseEnvironment;
import com.ourcreation.biomebasedcrops.GreenhouseRecipes;
import com.ourcreation.biomebasedcrops.GreenhouseSettings;
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

/** A visual English field index with one summary for each biome. */
public final class FieldGuideScreen extends Screen {
    private static final int INK = 0x3D2D20;
    private static final int FADED_INK = 0x6C5740;
    private static final ResourceLocation BOOK = ResourceLocation.withDefaultNamespace("textures/gui/book.png");
    private static GreenhouseEnvironment.Inspection lastSurvey;
    private int biomeOffset, view, materialOffset;
    private int bookX, bookY, bookWidth, bookHeight, pageWidth, visibleBiomes;
    private EditBox search;
    private String query = "";
    private List<Holder.Reference<Biome>> biomes = List.of(), filtered = List.of();
    private ResourceLocation selectedBiome;
    private PageButton indexPrevious, indexNext;
    private InkLink buildLink, backLink;
    private final List<IngredientHit> ingredients = new ArrayList<>();
    private final List<BiomeLink> biomeLinks = new ArrayList<>();
    private boolean surveyOpened;

    public FieldGuideScreen() { super(Component.literal("Biome field index")); }
    public static void open() { Minecraft.getInstance().setScreen(new FieldGuideScreen()); }
    public static void clearSurvey() { lastSurvey = null; }
    public static void updateSurveyIfOpen(GreenhouseEnvironment.Inspection inspection) {
        if (Minecraft.getInstance().screen instanceof FieldGuideScreen guide && guide.surveyOpened)
            openSurvey(inspection);
    }
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
        buildLink = addRenderableWidget(new InkLink(rightX(), bookY + bookHeight - 25,
                "How to build", () -> showView(2)));
        backLink = addRenderableWidget(new InkLink(rightX(), bookY + bookHeight - 25,
                "< Biome", () -> showView(0)));
        search = new EditBox(font, leftX(), bookY + 37, pageWidth, 14, Component.literal("Find a biome"));
        search.setMaxLength(128);
        search.setBordered(false);
        search.setTextColor(INK);
        search.setTextColorUneditable(FADED_INK);
        search.setHint(Component.literal("Find a biome...").withStyle(ChatFormatting.DARK_GRAY));
        search.setValue(query);
        search.setResponder(value -> {
            query = value;
            biomeOffset = view = materialOffset = 0;
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
        buildLink.visible = buildLink.active = view == 0;
        backLink.visible = backLink.active = view != 0;
    }
    private static void show(PageButton button, boolean visible) { button.visible = button.active = visible; }
    private void turnIndex(int direction) {
        biomeOffset = Math.clamp(biomeOffset + direction * visibleBiomes, 0, Math.max(0, filtered.size() - visibleBiomes));
        updateControls();
    }
    private void showView(int selected) {
        view = selected;
        updateControls();
    }
    private boolean matchingSurvey() {
        return surveyOpened && lastSurvey != null && selectedBiome != null && selectedBiome.equals(lastSurvey.targetBiome());
    }
    private GreenhouseRecipes.Recipe recipe() {
        return biomes.stream().filter(holder -> holder.key().location().equals(selectedBiome)).findFirst()
                .map(GreenhouseRecipes::forBiome).orElse(null);
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        drawBook(g);
        ingredients.clear();
        if (filtered.isEmpty()) wrap(g, "No matches", leftX(), bookY + 61, pageWidth, 2);
        GreenhouseRecipes.Recipe recipe = recipe();
        if (recipe != null) {
            Component heading = view == 0 ? EnglishGuideText.biomeName(selectedBiome) : Component.literal("How to build");
            g.drawString(font, fit(heading.copy().withStyle(ChatFormatting.BOLD), pageWidth), rightX(), bookY + 18, INK, false);
            String label = view == 0 ? "Required blocks" : "Example: Forest";
            g.drawString(font, fit(bold(label), pageWidth), rightX(), bookY + 35, INK, false);
            if (view == 0) drawMaterials(g, recipe);
            else drawRoom(g);
        }
        updateControls();
        for (var child : children()) if (child instanceof net.minecraft.client.gui.components.Renderable renderable)
            renderable.render(g, mouseX, mouseY, partialTick);
        ingredientAt(mouseX, mouseY).ifPresent(hit -> {
            List<Component> tooltip = new ArrayList<>(Screen.getTooltipFromItem(minecraft, hit.stack()));
            if (matchingSurvey()) {
                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(hit.stack().getItem());
                lastSurvey.requirements().stream().filter(progress -> progress.block().equals(itemId)).findFirst()
                        .ifPresent(progress -> tooltip.add(Component.literal("Found: " + progress.found() + "/" + progress.required())
                                .withStyle(ChatFormatting.GRAY)));
            }
            g.renderTooltip(font, tooltip, hit.stack().getTooltipImage(), hit.stack(), mouseX, mouseY);
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
        g.drawString(font, fit(bold("Biome field index"), pageWidth), leftX(), bookY + 18, INK, false);
        g.fill(leftX(), bookY + 51, leftX() + pageWidth - 2, bookY + 52, 0x997D7850);
        String range = filtered.isEmpty() ? "0" : (biomeOffset + 1) + "-" + Math.min(filtered.size(), biomeOffset + visibleBiomes) + "/" + filtered.size();
        drawFolio(g, range, leftX());
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
        var requirements = recipe.requirements();
        if (surveyOpened && lastSurvey != null && lastSurvey.targetBiome() == null) {
            line(g, surveyIssue(lastSurvey.status()), x, y);
            y += 13;
        }
        int columns = Math.max(1, pageWidth / 18);
        boolean scroll = scrollMaterials(recipe);
        int rows = scroll ? 1 : Math.max(1, (requirements.size() + columns - 1) / columns);
        materialOffset = scroll ? Math.clamp(materialOffset, 0, Math.max(0, requirements.size() - columns)) : 0;
        int visible = Math.min(requirements.size() - materialOffset, columns * rows);
        for (int i = 0; i < visible; i++) {
            int at = materialOffset + i, ix = x + i % columns * 18, iy = y + i / columns * 18;
            var requirement = requirements.get(at);
            ItemStack stack = specimenStack(requirement.block());
            stack.setCount(requirement.count());
            drawIngredient(g, stack, ix, iy);
            g.renderItemDecorations(font, stack, ix, iy, Integer.toString(requirement.count()));
        }
        int temperatureY = y + rows * 18 + 12;
        if (scroll) {
            g.drawString(font, "<> scroll", x, temperatureY, FADED_INK, false);
            temperatureY += 12;
        }
        Component temperature = Component.literal("Required temperature: "
                + EnglishGuideText.climateName(recipe.climate()).getString());
        List<FormattedCharSequence> temperatureLines = font.split(temperature, pageWidth - 2);
        for (int i = 0; i < temperatureLines.size(); i++)
            g.drawString(font, temperatureLines.get(i), x, temperatureY + i * 10, INK, false);
        int afterTemperature = temperatureY + temperatureLines.size() * 10;
        int statusHeight = 0;
        if (matchingSurvey()) {
            List<FormattedCharSequence> status = font.split(Component.literal(surveyIssue(lastSurvey.status())), pageWidth - 2);
            statusHeight = Math.min(2, status.size()) * 10 + 5;
            for (int i = 0; i < Math.min(2, status.size()); i++)
                g.drawString(font, status.get(i), x, afterTemperature + 5 + i * 10, INK, false);
        }
        int machineY = afterTemperature + statusHeight + 12;
        drawIngredient(g, new ItemStack(GreenhouseContent.AIR_CONDITIONER_ITEM.get()), x, machineY);
        g.drawString(font, fit(Component.literal("Air Conditioner"), pageWidth - 22), x + 22, machineY + 4, INK, false);
        wrap(g, "Power it; set target", x, machineY + 21, pageWidth, 2);
        ResourceLocation tan = ResourceLocation.fromNamespaceAndPath("toughasnails", "thermoregulator");
        if (tanOptional() && BuiltInRegistries.ITEM.containsKey(tan) && machineY + 63 < bookY + bookHeight - 32) {
            drawIngredient(g, new ItemStack(BuiltInRegistries.ITEM.get(tan)), x, machineY + 45);
            g.drawString(font, fit(Component.literal("Optional TAN"), pageWidth - 22), x + 22, machineY + 49, INK, false);
        }
    }

    private void drawRoom(GuiGraphics g) {
        int x = rightX(), y = bookY + 53, start = x + (pageWidth - 80) / 2;
        for (int col = 0; col < 5; col++) {
            drawIngredient(g, new ItemStack(Items.GLASS), start + col * 16, y);
            drawIngredient(g, new ItemStack(col == 1 || col == 3 ? Items.FARMLAND : Items.DIRT), start + col * 16, y + 48);
        }
        for (int row = 1; row < 3; row++) {
            drawIngredient(g, new ItemStack(Items.GLASS), start, y + row * 16);
            drawIngredient(g, new ItemStack(Items.GLASS), start + 64, y + row * 16);
        }
        ItemStack logs = new ItemStack(Items.OAK_LOG, 8);
        ItemStack leaves = new ItemStack(Items.OAK_LEAVES, 4);
        ItemStack birch = new ItemStack(Items.BIRCH_LOG, 2);
        drawIngredient(g, logs, start + 16, y + 16);
        g.renderItemDecorations(font, logs, start + 16, y + 16);
        drawIngredient(g, leaves, start + 32, y + 16);
        g.renderItemDecorations(font, leaves, start + 32, y + 16);
        drawIngredient(g, birch, start + 48, y + 16);
        g.renderItemDecorations(font, birch, start + 48, y + 16);
        drawIngredient(g, new ItemStack(Items.WHEAT), start + 16, y + 32);
        drawIngredient(g, new ItemStack(Items.WHEAT), start + 48, y + 32);
        int labelY = y + 68;
        line(g, "1. Seal all 6 sides", x, labelY);
        line(g, percent(GreenhouseSettings.roofGlassRatio()) + " roof: glass", x, labelY + 11);
        line(g, percent(GreenhouseSettings.wallGlassRatio()) + " walls: glass", x, labelY + 22);
        line(g, "2. Expose each block", x, labelY + 33);
        line(g, "3. Scan inside", x, labelY + 44);
        drawIngredient(g, new ItemStack(GreenhouseContent.PROBE.get()), x + pageWidth - 18, labelY + 42);
        line(g, "Blocks choose biome", x, labelY + 55);
    }

    private static String surveyIssue(String status) {
        return switch (status.substring(status.lastIndexOf('.') + 1)) {
            case "valid" -> "Valid greenhouse";
            case "scanning" -> "Surveying the room...";
            case "no_match", "missing_specimens" -> "Blocks missing";
            case "ambiguous" -> "Mixed biome layouts";
            case "too_large", "open", "outside" -> "Open / too large";
            case "no_glass" -> "More roof / wall glass needed";
            case "wrong_temperature" -> "Wrong climate";
            case "disabled" -> "Server disabled";
            case "unloaded" -> "Area not loaded";
            default -> "Scan inside";
        };
    }

    private boolean tanOptional() {
        return ModList.get().isLoaded("toughasnails") && Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.get();
    }
    private static Component bold(String value) { return Component.literal(value).withStyle(ChatFormatting.BOLD); }
    private static String percent(double ratio) {
        return java.math.BigDecimal.valueOf(ratio).multiply(java.math.BigDecimal.valueOf(100))
                .stripTrailingZeros().toPlainString() + "%";
    }
    private void line(GuiGraphics g, String value, int x, int y) { g.drawString(font, fit(Component.literal(value), pageWidth), x, y, INK, false); }
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
        Optional<IngredientHit> hit = ingredientAt(mouseX, mouseY);
        if ((button == 0 || button == 1) && hit.isPresent()) {
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
        int columns = Math.max(1, pageWidth / 18);
        if (view == 0 && recipe != null && scrollMaterials(recipe)
                && inside(mouseX, mouseY, rightX(), bookY + 53, pageWidth, 38)) {
            materialOffset = Math.clamp(materialOffset - (int)Math.signum(scrollY), 0, recipe.specimens().size() - columns);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
    public Rect2i bookBounds() { return new Rect2i(bookX, bookY, bookWidth, bookHeight); }
    public Optional<IngredientHit> ingredientAt(double mouseX, double mouseY) {
        return ingredients.stream().filter(hit -> inside(mouseX, mouseY, hit.bounds().getX(), hit.bounds().getY(),
                hit.bounds().getWidth(), hit.bounds().getHeight())).findFirst();
    }
    public Optional<IngredientHit> viewerIngredientAt(double mouseX, double mouseY) {
        return search != null && search.isFocused() ? Optional.empty() : ingredientAt(mouseX, mouseY);
    }
    private void drawIngredient(GuiGraphics g, ItemStack stack, int x, int y) {
        if (stack.isEmpty()) return;
        g.renderItem(stack, x, y);
        ingredients.add(new IngredientHit(stack.copy(), new Rect2i(x, y, 16, 16)));
    }
    public record IngredientHit(ItemStack stack, Rect2i bounds) {}
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
    private final class InkLink extends AbstractButton {
        private final Runnable action;
        private InkLink(int x, int y, String label, Runnable action) {
            super(x, y, pageWidth, 12, Component.literal(label)); this.action = action;
        }
        @Override public void onPress() { action.run(); }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
        @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            g.drawString(font, fit(getMessage(), width), getX(), getY() + 1, INK, false);
            if (isFocused()) g.fill(getX(), getY() + 10, getX() + font.width(getMessage()), getY() + 11, 0xAA3D2D20);
        }
    }
    private final class BiomeLink extends AbstractButton {
        private final int row;
        private BiomeLink(int row) { super(leftX(),bookY+56+row*22,pageWidth-2,21,Component.empty()); this.row=row; }
        @Override public void onPress() {
            int index=biomeOffset+row;
            if(index<filtered.size()) {
                selectedBiome=filtered.get(index).key().location(); view=materialOffset=0; updateControls();
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
