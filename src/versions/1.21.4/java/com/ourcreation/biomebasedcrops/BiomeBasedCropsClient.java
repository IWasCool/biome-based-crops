package com.ourcreation.biomebasedcrops;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@Mod(value = BiomeBasedCrops.MODID, dist = Dist.CLIENT)
public class BiomeBasedCropsClient {
    public BiomeBasedCropsClient(ModContainer container, net.neoforged.bus.api.IEventBus modBus) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) ->
                event.register(AirConditionerMenu.TYPE.get(), com.ourcreation.biomebasedcrops.client.AirConditionerScreen::new));
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent event) ->
                event.registerSpriteSet(GreenhouseContent.AIRFLOW_PARTICLE.get(), com.ourcreation.biomebasedcrops.client.AirflowParticle.Provider::new));
        modBus.addListener(com.ourcreation.biomebasedcrops.client.InstrumentClient::registerProperties);
        NeoForge.EVENT_BUS.addListener(com.ourcreation.biomebasedcrops.client.InstrumentClient::tick);
        NeoForge.EVENT_BUS.addListener(BiomeBasedCropsClient::onTooltip);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) -> {
            GreenhouseClientBiomes.clear();
            com.ourcreation.biomebasedcrops.client.InstrumentClient.clear();
            GreenhouseRecipes.clearClientRecipes();
            com.ourcreation.biomebasedcrops.client.EnglishGuideText.clearCache();
        });
    }

    private static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (TemperatureInstrumentCompatibility.updateTooltip(BuiltInRegistries.ITEM.getKey(stack.getItem()), event.getToolTip())) return;
        if (!ClientConfig.SPEC.isLoaded() || !Config.ENABLE_TOOLTIPS.get()) {
            return;
        }

        // SERVER rules are synced when joining a world; no authoritative rules exist in menus.
        if (Config.SPEC.isLoaded()) {
            CropMatcher.profileForItem(stack).ifPresent(profile -> {
                MutableComponent line = Component.translatable("biome_based_crops.tooltip.climate", profile.climateListComponent())
                        .withStyle(ChatFormatting.GRAY);
                event.getToolTip().add(line);
            });
        }

        Player player = event.getEntity();
        if (player != null && CropMatcher.isThermometer(stack)) {
            CropClimate climate = currentClimate(player.level(), player.blockPosition());
            event.getToolTip().add(Component.translatable("biome_based_crops.tooltip.local_climate", climate.component())
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    private static CropClimate currentClimate(Level level, BlockPos pos) {
        return GreenhouseEnvironment.effectiveClimate(level, pos);
    }
}
