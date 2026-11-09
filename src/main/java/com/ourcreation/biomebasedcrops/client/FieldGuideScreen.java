package com.ourcreation.biomebasedcrops.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

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

/** A visual English field index with one summary for each biome. */
public final class FieldGuideScreen extends Screen {
    private static final int INK = 0x3D2D20;
    private static final int FADED_INK = 0x6C5740;
    private static final ResourceLocation BOOK = ResourceLocation.withDefaultNamespace("textures/gui/book.png");
    private static GreenhouseEnvironment.Inspection lastSurvey;
    private int biomeOffset, materialOffset, roomVolume;
    private int bookX, bookY, bookWidth, bookHeight, pageWidth, visibleBiomes;
    private EditBox search;
    private String query = "";
    private List<Holder.Reference<Biome>> biomes = List.of(), filtered = List.of();
    private ResourceLocation selectedBiome;
    private PageButton indexPrevious, indexNext;
    private final List<IngredientHit> ingredients = new ArrayList<>();
    private final List<BiomeLink> biomeLinks = new ArrayList<>();
    private boolean surveyOpened;

    public FieldGuideScreen() { super(Component.literal("Biome field index")); }
    public static void open() { open(0); }
    public static void open(int roomVolume) {
        FieldGuideScreen screen = new FieldGuideScreen();
        screen.roomVolume = Math.max(0, roomVolume);
        Minecraft.getInstance().setScreen(screen);
    }
    public static void clearSurvey() { lastSurvey = null; }
    public static void updateRoomVolumeIfOpen(int roomVolume) {
        if (Minecraft.getInstance().screen instanceof FieldGuideScreen guide)
            guide.roomVolume = Math.max(0, roomVolume);
    }
    public static void updateSurveyIfOpen(GreenhouseEnvironment.Inspection inspection) {
        if (Minecraft.getInstance().screen instanceof FieldGuideScreen guide && guide.surveyOpened)
            openSurvey(inspection);
    }
    public static void openSurvey(GreenhouseEnvironment.Inspection inspection) {
        lastSurvey = inspection;
        FieldGuideScreen screen = new FieldGuideScreen();
        screen.selectedBiome = inspection.targetBiome();
        screen.surveyOpened = true;
        screen.roomVolume = Math.max(0, inspection.roomVolume());
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
        search = new EditBox(font, leftX(), bookY + 37, pageWidth, 14, Component.literal("Find a biome"));
        search.setMaxLength(128);
        search.setBordered(false);
        search.setTextColor(INK);
        search.setTextColorUneditable(FADED_INK);
        search.setHint(Component.literal("Find a biome...").withStyle(ChatFormatting.DARK_GRAY));
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
    }
    private static void show(PageButton button, boolean visible) { button.visible = button.active = visible; }
    private void turnIndex(int direction) {
        biomeOffset = Math.clamp(biomeOffset + direction * visibleBiomes, 0, Math.max(0, filtered.size() - visibleBiomes));
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
        if (recipe != null) drawSummary(g, recipe);
        updateControls();
        for (var child : children()) if (child instanceof net.minecraft.client.gui.components.Renderable renderable)
            renderable.render(g, mouseX, mouseY, partialTick);
        ingredientAt(mouseX, mouseY).ifPresent(hit -> {
            List<Component> extraLines = new ArrayList<>();
            if (hit.stack().getCount() > 1)
                extraLines.add(Component.literal("Required: " + hit.stack().getCount()).withStyle(ChatFormatting.GRAY));
            if (matchingSurvey()) {
                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(hit.stack().getItem());
                lastSurvey.requirements().stream().filter(progress -> progress.block().equals(itemId)).findFirst()
                        .ifPresent(progress -> extraLines.add(Component.literal("Found: " + progress.found() + "/" + progress.required())
                                .withStyle(ChatFormatting.GRAY)));
            }
            if (!GreenhouseViewerBridge.renderTooltip(g, hit.stack(), extraLines, mouseX, mouseY)) {
                List<Component> tooltip = new ArrayList<>(Screen.getTooltipFromItem(minecraft, hit.stack()));
                tooltip.addAll(extraLines);
                g.renderTooltip(font, tooltip, hit.stack().getTooltipImage(), hit.stack(), mouseX, mouseY);
            }
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

    private void drawSummary(GuiGraphics g, GreenhouseRecipes.Recipe recipe) {
        int x = rightX(), y = bookY + 18;
        Component name = EnglishGuideText.biomeName(selectedBiome).copy().withStyle(ChatFormatting.BOLD);
        List<FormattedCharSequence> nameLines = font.split(name, pageWidth);
        for (int i = 0; i < Math.min(3, nameLines.size()); i++)
            g.drawString(font, nameLines.get(i), x, y + i * 10, INK, false);
        y += Math.min(3, nameLines.size()) * 10 + 2;
        int reference = GreenhouseRecipes.referenceVolume();
        String context = roomVolume > 0 ? "Room: " + roomVolume : "Per " + reference + " blocks";
        y += drawLines(g, Component.literal(context), x, y, pageWidth, 2) + 2;

        var requirements = recipe.requirements();
        int columns = Math.max(1, pageWidth / 20);
        materialOffset = Math.clamp(materialOffset, 0, Math.max(0, requirements.size() - columns));
        int visible = Math.min(requirements.size() - materialOffset, columns);
        for (int i = 0; i < visible; i++) {
            var requirement = requirements.get(materialOffset + i);
            int required = GreenhouseRecipes.scaledCount(requirement.count(), roomVolume, reference);
            ItemStack stack = specimenStack(requirement.block());
            stack.setCount(required);
            int ix = x + i * 20;
            drawIngredient(g, stack, ix, y);
            drawQuantity(g, stack, ix, y, required);
        }
        materialRowY = y;
        y += 18;
        if (requirements.size() > columns) {
            line(g, "<> scroll materials", x, y);
            y += 10;
        }
        y += 6;
        y += drawLines(g, Component.literal("Required temperature: "
                + EnglishGuideText.targetTemperatures(recipe.climate())), x, y, pageWidth, 3);
        y += 4;
        line(g, "Seal + glass roof", x, y);
        y += 10;
        line(g, "Roof:" + percent(GreenhouseSettings.roofGlassRatio()) + " Walls:" + percent(GreenhouseSettings.wallGlassRatio()), x, y);
        y += 10;
        line(g, "Expose blocks; scan", x, y);
        y += 10;
        if (surveyOpened && lastSurvey != null) {
            String status = matchingSurvey() || lastSurvey.targetBiome() == null ? surveyIssue(lastSurvey.status()) : "";
            if (!status.isEmpty()) {
                y += 3;
                line(g, status, x, y);
            }
        }

        int machineY = bookY + bookHeight - 49;
        g.drawString(font, "Thermoregulator", x, machineY, INK, false);
        drawIngredient(g, new ItemStack(GreenhouseContent.AIR_CONDITIONER_ITEM.get()), x, machineY + 12);
        drawLines(g, Component.literal("Power; set target"), x + 22, machineY + 12, pageWidth - 22, 2);
    }

    private int materialRowY;

    private void drawQuantity(GuiGraphics g, ItemStack stack, int x, int y, int count) {
        if (count < 1000) {
            g.renderItemDecorations(font, stack, x, y, Integer.toString(count));
            return;
        }
        String compact = String.format(Locale.ROOT, count < 10000 ? "%.1fk" : "%.0fk", count / 1000.0).replace(".0k", "k");
        float scale = Math.min(1.0F, 17.0F / font.width(compact));
        g.pose().pushPose();
        g.pose().translate(x + 17, y + 17, 200);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, compact, -font.width(compact), -font.lineHeight, 0xFFFFFF, true);
        g.pose().popPose();
    }

    private int drawLines(GuiGraphics g, Component component, int x, int y, int width, int maxLines) {
        List<FormattedCharSequence> lines = font.split(component, width);
        int count = Math.min(maxLines, lines.size());
        for (int i = 0; i < count; i++) g.drawString(font, lines.get(i), x, y + i * 10, INK, false);
        return count * 10;
    }

    private static String surveyIssue(String status) {
        return switch (status.substring(status.lastIndexOf('.') + 1)) {
            case "valid" -> "Valid greenhouse";
            case "scanning" -> "Surveying the room...";
            case "no_match", "missing_specimens" -> "Blocks missing";
            case "ambiguous" -> "Mixed biome layouts";
            case "too_large", "open", "outside" -> "Open / too large";
            case "no_glass" -> "More roof / wall glass needed";
            case "wrong_temperature" -> "Climate not ready";
            case "filling_air" -> "Air not ready";
            case "disabled" -> "Server disabled";
            case "unloaded" -> "Area not loaded";
            default -> "Scan inside";
        };
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
        int columns = Math.max(1, pageWidth / 20);
        if (recipe != null && recipe.requirements().size() > columns
                && inside(mouseX, mouseY, rightX(), materialRowY, pageWidth, 28)) {
            materialOffset = Math.clamp(materialOffset - (int)Math.signum(scrollY), 0, recipe.requirements().size() - columns);
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
        return ingredientAt(mouseX, mouseY);
    }
    private void drawIngredient(GuiGraphics g, ItemStack stack, int x, int y) {
        if (stack.isEmpty()) return;
        g.renderItem(stack, x, y);
        ingredients.add(new IngredientHit(stack.copy(), new Rect2i(x, y, 16, 16)));
    }
    public record IngredientHit(ItemStack stack, Rect2i bounds) {}
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
                selectedBiome=filtered.get(index).key().location(); materialOffset=0; updateControls();
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
