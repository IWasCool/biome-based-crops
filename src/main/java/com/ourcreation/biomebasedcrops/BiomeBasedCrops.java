package com.ourcreation.biomebasedcrops;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(BiomeBasedCrops.MODID)
public class BiomeBasedCrops {
    public static final String MODID = "biome_based_crops";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BiomeBasedCrops(IEventBus modEventBus, ModContainer modContainer) {
        GreenhouseContent.register(modEventBus);
        modEventBus.addListener(ConfigEvents::onConfigChanged);
        modEventBus.addListener(GrowthSettingsPayload::onRegisterPayloadHandlers);
        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.SERVER, GreenhouseSettings.SPEC, "biome_based_crops-greenhouse.toml");

        NeoForge.EVENT_BUS.addListener(CommonEvents::onCropGrow);
        NeoForge.EVENT_BUS.addListener(CommonEvents::onBonemeal);
        NeoForge.EVENT_BUS.addListener(CommonEvents::onRightClickItem);
        NeoForge.EVENT_BUS.addListener(CommonEvents::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(GrowthCommands::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.TagsUpdatedEvent event) -> {
            GreenhouseEnvironment.clearCache();
            GreenhouseRecipes.clearCache();
        });
    }

}
