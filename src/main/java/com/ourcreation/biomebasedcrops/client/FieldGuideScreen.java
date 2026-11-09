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

/** A visual English field index with one summary for each biome. */
public final class FieldGuideScreen extends Screen {
    private static final int INK = 0x3D2D20;
    private static final int FADED_INK = 0x6C5740;
    private static final ResourceLocation BOOK = ResourceLocation.withDefaultNamespace("textures/gui/book.png");
    private static GreenhouseEnvironment.Inspection lastSurvey;
    private int biomeOffset, view, materialOffset, selectedMaterial;
    private int bookX, bookY, bookWidth, bookHeight, pageWidth, visibleBiomes;
    private EditBox search;
    private String query = "";
    private List<Holder.Reference<Biome>> biomes = List.of(), filtered = List.of();
    private ResourceLocation selectedBiome;
    private PageButton indexPrevious, indexNext;
    private InkLink temperatureLink, buildLink, backLink;
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
        temperatureLink = addRenderableWidget(new InkLink(rightX(), bookY + bookHeight - 40,
                "Temperature", () -> showView(1)));
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
            biomeOffset = view = materialOffset = selectedMaterial = 0;
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
        temperatureLink.visible = temperatureLink.active = view == 0;
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
        return lastSurvey != null && selectedBiome != null && selectedBiome.equals(lastSurvey.targetBiome());
    }
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
            Component heading = view == 0 ? EnglishGuideText.biomeName(selectedBiome) : Component.literal(view == 1 ? "Temperature" : "How to build");
            g.drawString(font, fit(heading.copy().withStyle(ChatFormatting.BOLD), pageWidth), rightX(), bookY + 18, INK, false);
            String label = view == 0 ? "Required blocks" : view == 1 ? "Heat and cold" : "Example: Forest";
            g.drawString(font, fit(bold(label), pageWidth), rightX(), bookY + 35, INK, false);
            if (view == 0) drawMaterials(g, recipe);
            else if (view == 1) drawTemperature(g);
            else drawRoom(g);
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
            if (at == selectedMaterial) g.fill(ix - 1, iy - 1, ix + 17, iy + 17, 0x30A58B5B);
            g.renderItem(stack, ix, iy);
            g.renderItemDecorations(font, stack, ix, iy, Integer.toString(requirement.count()));
        }
        selectedMaterial = Math.clamp(selectedMaterial, 0, Math.max(0, requirements.size() - 1));
        int caption = y + 2 + rows * 18;
        if (!requirements.isEmpty()) {
            var requirement = requirements.get(selectedMaterial);
            g.drawString(font, fit(EnglishGuideText.blockName(requirement.block()), pageWidth - (scroll ? 18 : 0)), x, caption, INK, false);
            String need = "Need " + requirement.count();
            if (matchingSurvey()) {
                int found = lastSurvey.requirements().stream().filter(progress -> progress.block().equals(requirement.block()))
                        .mapToInt(GreenhouseEnvironment.RequirementProgress::found).findFirst().orElse(0);
                need += " | " + found + "/" + requirement.count();
            }
            line(g, need, x, caption + 11);
            if (scroll) g.drawString(font, "<>", x + pageWidth - 14, caption, INK, false);
        }
        CropClimate local = matchingSurvey() ? lastSurvey.climate() : nativeClimate();
        int thermalY = caption + 27;
        line(g, (matchingSurvey() ? "Room: " : "Natural: ") + EnglishGuideText.climateName(local).getString(), x, thermalY);
        line(g, "Required: " + EnglishGuideText.climateName(recipe.climate()).getString(), x, thermalY + 11);
        int cell = pageWidth / 5;
        int[] paper = {0xFFCCDCE1, 0xFFD5DDDA, 0xFFE2D7BD, 0xFFE5CAA7, 0xFFE3B593};
        for (int i = 0; i < 5; i++) {
            int cx = x + cell * i, cy = thermalY + 24;
            g.fill(cx, cy, cx + cell - 1, cy + 9, paper[i]);
            if (i == recipe.climate().ordinal()) border(g, cx, cy, cell - 1, 9);
            if (i == local.ordinal()) g.fill(cx + cell / 2 - 1, cy + 10, cx + cell / 2 + 2, cy + 12, 0xFF3D2D20);
        }
        int delta = recipe.climate().ordinal() - local.ordinal();
        line(g, delta == 0 ? "Climate matches" : delta > 0 ? "Needs warming" : "Needs cooling", x, thermalY + 42);
        String status = !GreenhouseSettings.enabled() ? "Greenhouses disabled"
                : matchingSurvey() ? surveyIssue(lastSurvey.status()) : "Scan to check";
        line(g, status, x, thermalY + 55);
    }

    private void drawRoom(GuiGraphics g) {
        int x = rightX(), y = bookY + 53, start = x + (pageWidth - 80) / 2;
        for (int col = 0; col < 5; col++) {
            g.renderItem(new ItemStack(Items.GLASS), start + col * 16, y);
            g.renderItem(new ItemStack(col == 1 || col == 3 ? Items.FARMLAND : Items.DIRT), start + col * 16, y + 48);
        }
        for (int row = 1; row < 3; row++) {
            g.renderItem(new ItemStack(Items.GLASS), start, y + row * 16);
            g.renderItem(new ItemStack(Items.GLASS), start + 64, y + row * 16);
        }
        ItemStack logs = new ItemStack(Items.OAK_LOG, 8);
        ItemStack leaves = new ItemStack(Items.OAK_LEAVES, 4);
        ItemStack birch = new ItemStack(Items.BIRCH_LOG, 2);
        g.renderItem(logs, start + 16, y + 16);
        g.renderItemDecorations(font, logs, start + 16, y + 16);
        g.renderItem(leaves, start + 32, y + 16);
        g.renderItemDecorations(font, leaves, start + 32, y + 16);
        g.renderItem(birch, start + 48, y + 16);
        g.renderItemDecorations(font, birch, start + 48, y + 16);
        g.renderItem(new ItemStack(Items.WHEAT), start + 16, y + 32);
        g.renderItem(new ItemStack(Items.WHEAT), start + 48, y + 32);
        int labelY = y + 68;
        line(g, "1. Seal all 6 sides", x, labelY);
        line(g, "4+ glass; roof too", x, labelY + 11);
        int insideWidth = GreenhouseSettings.radius() * 2 + 1;
        line(g, "Inside: " + insideWidth + "x" + insideWidth + "x5 max", x, labelY + 22);
        line(g, "2. Expose each block", x, labelY + 33);
        line(g, "3. Scan inside", x, labelY + 44);
        g.renderItem(new ItemStack(GreenhouseContent.PROBE.get()), x + pageWidth - 18, labelY + 42);
        line(g, "Blocks choose biome", x, labelY + 55);
    }

    private void drawTemperature(GuiGraphics g) {
        int x = rightX(), y = bookY + 53, cell = pageWidth / 4;
        Item[] heat = {Items.FIRE_CHARGE, Items.CAMPFIRE, Items.MAGMA_BLOCK, Items.LAVA_BUCKET};
        String[] hotNames = {"Fire", "Camp", "Mag.", "Lava"};
        String[] hotSteps = {"+1", "+1", "+2", "+4"};
        Item[] cold = {Items.SNOW_BLOCK, Items.ICE, Items.PACKED_ICE, Items.BLUE_ICE};
        String[] coldNames = {"Snow", "Ice", "Pack", "Blue"};
        String[] coldSteps = {"-1", "-2", "-3", "-4"};
        for (int row = 0; row < 2; row++) {
            for (int i = 0; i < 4; i++) {
                int ix = x + cell * i, iy = y + row * 42;
                g.renderItem(new ItemStack(row == 0 ? heat[i] : cold[i]), ix + (cell - 16) / 2, iy);
                String name = row == 0 ? hotNames[i] : coldNames[i];
                String steps = row == 0 ? hotSteps[i] : coldSteps[i];
                g.drawString(font, name, ix + (cell - font.width(name)) / 2, iy + 18, INK, false);
                g.drawString(font, bold(steps), ix + (cell - font.width(bold(steps))) / 2, iy + 29, INK, false);
            }
        }
        line(g, "Max heat - max cold", x, y + 84);
        line(g, "No stacking", x, y + 95);
        line(g, "Range: " + GreenhouseSettings.radius() + " blocks", x, y + 106);
        line(g, "Floor to 3 above", x, y + 117);
        GreenhouseRecipes.Recipe target = recipe();
        String mode = target == null ? "either" : switch (target.climate()) {
            case ICY, COLD -> "cooling";
            case WARM, HOT -> "heating";
            case NEUTRAL -> "either";
        };
        String note = !GreenhouseSettings.ambientEnabled() ? "Heat/cold disabled"
                : tanOptional() ? "Optional TAN " + mode : "";
        if (!note.isEmpty()) line(g, note, x, y + 129);
    }

    private static String surveyIssue(String status) {
        return switch (status.substring(status.lastIndexOf('.') + 1)) {
            case "valid" -> "Valid greenhouse";
            case "no_match", "missing_specimens" -> "Blocks missing";
            case "ambiguous" -> "Mixed biome layouts";
            case "too_large", "open", "outside" -> "Open / too large";
            case "no_glass" -> "4+ glass; roof too";
            case "wrong_temperature" -> "Wrong climate";
            case "disabled" -> "Server disabled";
            case "unloaded" -> "Area not loaded";
            default -> "Scan inside";
        };
    }

    private CropClimate nativeClimate() {
        BlockPos pos = minecraft.player == null ? BlockPos.ZERO : minecraft.player.blockPosition();
        return minecraft.level == null ? CropClimate.NEUTRAL : CropClimate.at(minecraft.level.getBiome(pos), pos);
    }
    private boolean tanOptional() {
        return ModList.get().isLoaded("toughasnails") && Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.get();
    }
    private static Component bold(String value) { return Component.literal(value).withStyle(ChatFormatting.BOLD); }
    private void line(GuiGraphics g, String value, int x, int y) { g.drawString(font, fit(Component.literal(value), pageWidth), x, y, INK, false); }
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
        if (button == 0 && view == 0 && recipe != null) {
            int columns = Math.max(1, pageWidth / 18);
            int rows = scrollMaterials(recipe) ? 1 : Math.max(1, (recipe.specimens().size() + columns - 1) / columns);
            int y = bookY + 53 + (surveyOpened && lastSurvey != null && lastSurvey.targetBiome() == null ? 13 : 0);
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
        if (view == 0 && recipe != null && scrollMaterials(recipe)
                && inside(mouseX, mouseY, rightX(), bookY + 53, pageWidth, 38)) {
            materialOffset = Math.clamp(materialOffset - (int)Math.signum(scrollY), 0, recipe.specimens().size() - columns);
            selectedMaterial = materialOffset; return true;
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
                selectedBiome=filtered.get(index).key().location(); view=materialOffset=selectedMaterial=0; updateControls();
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
