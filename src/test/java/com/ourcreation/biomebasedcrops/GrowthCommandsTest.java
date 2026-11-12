package com.ourcreation.biomebasedcrops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import com.electronwill.nightconfig.core.file.FileWatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.loading.FMLConfig;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(EphemeralTestServerProvider.class)
class GrowthCommandsTest {
    private static boolean originalCrops;
    private static boolean originalTrees;

    @BeforeAll
    static void captureConfig(MinecraftServer server) {
        // These tests exercise commands and their synchronous disk writes, not
        // external file editing. NeoForge's watcher can reload an intermediate
        // save on another thread between successive command assertions. Remove
        // only this ephemeral server config's watch for the JUnit session.
        if (!FMLConfig.getBoolConfigValue(FMLConfig.ConfigValue.DISABLE_CONFIG_WATCHER)) {
            FileWatcher.defaultInstance().removeWatchFuture(configPath(server))
                    .orTimeout(10, TimeUnit.SECONDS).join();
        }
        originalCrops = Config.ENABLE_CROP_BIOME_DEPENDENCIES.get();
        originalTrees = Config.ENABLE_SAPLING_BIOME_DEPENDENCIES.get();
    }

    @BeforeEach
    void enableBothCategories() {
        Config.ENABLE_CROP_BIOME_DEPENDENCIES.set(true);
        Config.ENABLE_SAPLING_BIOME_DEPENDENCIES.set(true);
    }

    @AfterEach
    void restoreConfig() {
        Config.ENABLE_CROP_BIOME_DEPENDENCIES.set(originalCrops);
        Config.ENABLE_SAPLING_BIOME_DEPENDENCIES.set(originalTrees);
        Config.SPEC.save();
    }

    @Test
    void disablingAndEnablingBothCategoriesChangesPlacedPlantsAndTooltips(MinecraftServer server)
            throws CommandSyntaxException {
        execute(server, "biomebasedcrops disable", 2);
        assertSettings(false, false);
        assertTrue(CropMatcher.profileForState(Blocks.WHEAT.defaultBlockState()).isEmpty());
        assertTrue(CropMatcher.profileForState(Blocks.OAK_SAPLING.defaultBlockState()).isEmpty());
        assertTrue(CropMatcher.profileForItem(new ItemStack(Items.WHEAT_SEEDS)).isEmpty());
        assertTrue(CropMatcher.profileForItem(new ItemStack(Items.OAK_SAPLING)).isEmpty());

        execute(server, "biomebasedcrops enable", 2);
        assertSettings(true, true);
        assertTrue(CropMatcher.profileForState(Blocks.WHEAT.defaultBlockState()).isPresent());
        assertTrue(CropMatcher.profileForState(Blocks.OAK_SAPLING.defaultBlockState()).isPresent());
        assertTrue(CropMatcher.profileForItem(new ItemStack(Items.WHEAT_SEEDS)).isPresent());
        assertTrue(CropMatcher.profileForItem(new ItemStack(Items.OAK_SAPLING)).isPresent());
    }

    @Test
    void categoryCommandsPreserveTheOtherCategory(MinecraftServer server) throws CommandSyntaxException {
        execute(server, "biomebasedcrops crops disable", 2);
        assertSettings(false, true);
        execute(server, "biomebasedcrops trees disable", 2);
        assertSettings(false, false);
        execute(server, "biomebasedcrops crops enable", 2);
        assertSettings(true, false);
        execute(server, "biomebasedcrops trees enable", 2);
        assertSettings(true, true);
    }

    @Test
    void ordinaryPlayersCanCheckStatusWithoutChangingSettings(MinecraftServer server)
            throws CommandSyntaxException {
        Config.ENABLE_SAPLING_BIOME_DEPENDENCIES.set(false);
        for (String command : new String[]{"biomebasedcrops status", "biomebasedcrops crops status",
                "biomebasedcrops trees status"}) {
            execute(server, command, 0);
            assertSettings(true, false);
        }
    }

    @Test
    void ordinaryPlayersCannotChangeWorldRules(MinecraftServer server) {
        for (String command : new String[]{"biomebasedcrops disable", "biomebasedcrops enable",
                "biomebasedcrops crops disable", "biomebasedcrops crops enable",
                "biomebasedcrops trees disable", "biomebasedcrops trees enable"}) {
            assertThrows(CommandSyntaxException.class, () -> execute(server, command, 0), command);
            assertSettings(true, true);
        }
    }

    @Test
    void invalidCommandsLeaveSettingsUnchanged(MinecraftServer server) {
        for (String command : new String[]{"biomebasedcrops crops invalid", "biomebasedcrops trees invalid",
                "biomebasedcrops disable extra", "biomebasedcrops flowers disable"}) {
            assertThrows(CommandSyntaxException.class, () -> execute(server, command, 2), command);
            assertSettings(true, true);
        }
    }

    @Test
    void commandsPersistChoicesToTheActiveServerConfig(MinecraftServer server)
            throws CommandSyntaxException, IOException {
        execute(server, "biomebasedcrops crops disable", 2);
        assertPersisted(server, false, true);
        execute(server, "biomebasedcrops trees disable", 2);
        assertPersisted(server, false, false);
        execute(server, "biomebasedcrops enable", 2);
        assertPersisted(server, true, true);
    }

    private static void execute(MinecraftServer server, String command, int permission)
            throws CommandSyntaxException {
        CommandSourceStack source = server.createCommandSourceStack().withPermission(permission).withSuppressedOutput();
        server.getCommands().getDispatcher().execute(command, source);
    }

    private static void assertSettings(boolean crops, boolean trees) {
        assertEquals(crops, Config.ENABLE_CROP_BIOME_DEPENDENCIES.get(), "crop restrictions");
        assertEquals(trees, Config.ENABLE_SAPLING_BIOME_DEPENDENCIES.get(), "tree restrictions");
    }

    private static void assertPersisted(MinecraftServer server, boolean crops, boolean trees) throws IOException {
        String contents = Files.readString(configPath(server));
        assertTrue(contents.matches("(?s).*\\benableCropBiomeDependencies\\s*=\\s*" + crops + "\\b.*"), contents);
        assertTrue(contents.matches("(?s).*\\benableSaplingBiomeDependencies\\s*=\\s*" + trees + "\\b.*"), contents);
    }

    private static Path configPath(MinecraftServer server) {
        String filename = BiomeBasedCrops.MODID + "-server.toml";
        Path worldConfig = server.getWorldPath(new LevelResource("serverconfig")).resolve(filename);
        return Files.exists(worldConfig) ? worldConfig : FMLPaths.CONFIGDIR.get().resolve(filename);
    }
}
