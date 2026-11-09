package com.ourcreation.biomebasedcrops;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
    public BiomeBasedCropsClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        NeoForge.EVENT_BUS.addListener(BiomeBasedCropsClient::onTooltip);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) -> {
            GreenhouseClientBiomes.clear();
            GreenhouseRecipes.clearClientRecipes();
            com.ourcreation.biomebasedcrops.client.EnglishGuideText.clearCache();
            com.ourcreation.biomebasedcrops.client.FieldGuideScreen.clearSurvey();
        });
    }

    private static void onTooltip(ItemTooltipEvent event) {
        if (!ClientConfig.SPEC.isLoaded() || !Config.ENABLE_TOOLTIPS.get()) {
            return;
        }

        ItemStack stack = event.getItemStack();
        // SERVER rules are synced when joining a world; no authoritative rules exist in menus.
        if (Config.SPEC.isLoaded()) {
            CropMatcher.profileForItem(stack).ifPresent(profile -> {
                String key = profile.hasBiomeRule() ? "biome_based_crops.tooltip.biomes" : "biome_based_crops.tooltip.climate";
                MutableComponent line = Component.translatable(key, profile.requirementsComponent())
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
