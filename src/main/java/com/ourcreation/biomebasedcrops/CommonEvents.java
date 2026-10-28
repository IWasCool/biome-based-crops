package com.ourcreation.biomebasedcrops;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.BonemealEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;
import net.neoforged.fml.ModList;

public final class CommonEvents {
    private CommonEvents() {
    }

    public static boolean canGrowNaturally(Level level, BlockPos pos, BlockState state) {
        if (!Config.SPEC.isLoaded() || !Config.BLOCK_NATURAL_GROWTH.get()) {
            return true;
        }
        return CropMatcher.profileForState(state)
                .map(profile -> canGrow(level, pos, profile))
                .orElse(true);
    }

    /** Shared guard for modded fertilizers that do not fire the vanilla bonemeal event. */
    public static boolean canGrowWithFertilizer(Level level, BlockPos pos, BlockState state) {
        if (!Config.SPEC.isLoaded() || !Config.BLOCK_BONEMEAL_GROWTH.get()) {
            return true;
        }
        BlockState plantState = SnowCoveredPlantResolver.resolve(level, pos, state);
        return CropMatcher.profileForState(plantState)
                .map(profile -> canGrow(level, pos, profile))
                .orElse(true);
    }

    public static void onCropGrow(CropGrowEvent.Pre event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide) {
            return;
        }
        BlockState state = event.getState();
        BlockPos pos = event.getPos();
        // Soil-based growth boosts can call performBonemeal directly without ticking
        // the plant or firing BonemealEvent. Guard events whose position is the plant.
        // Cactus and cave-vine ticks report an empty destination instead; their source
        // climate has already been checked by PlantGrowthMixin.
        if (level.getBlockState(pos).is(state.getBlock())
                && !canGrowNaturally(level, pos, SnowCoveredPlantResolver.resolve(level, pos, state))) {
            event.setResult(CropGrowEvent.Pre.Result.DO_NOT_GROW);
        }
    }

    public static void onBonemeal(BonemealEvent event) {
        if (!Config.SPEC.isLoaded() || !Config.BLOCK_BONEMEAL_GROWTH.get() || !event.isValidBonemealTarget()) {
            return;
        }

        BlockState state = SnowCoveredPlantResolver.resolve(event.getLevel(), event.getPos(), event.getState());
        var profile = CropMatcher.profileForState(state);
        if (profile.isEmpty() || canGrow(event.getLevel(), event.getPos(), profile.get())) {
            return;
        }

        event.setSuccessful(false);
        Player player = event.getPlayer();
        if (player != null && !event.getLevel().isClientSide) {
            player.displayClientMessage(blockedMessage(profile.get()), true);
        }
    }

    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        showThermometerClimate(event.getEntity(), event.getHand());
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        showThermometerClimate(event.getEntity(), event.getHand());
    }

    private static boolean canGrow(Level level, BlockPos pos, CropProfile profile) {
        return profile.allows(CropClimate.at(level.getBiome(pos), pos))
                || ThermoregulatorDetector.canSupportProfile(level, pos, profile);
    }

    private static void showThermometerClimate(Player player, InteractionHand hand) {
        if (player.level().isClientSide || !CropMatcher.isThermometer(player.getItemInHand(hand))) {
            return;
        }

        CropClimate climate = currentClimate(player.level(), player.blockPosition());
        MutableComponent message = Component.translatable("biome_based_crops.message.biome_climate", climate.component())
                .withStyle(ChatFormatting.GRAY);

        if (Config.SPEC.isLoaded() && ThermoregulatorDetector.hasThermoregulatorNearby(player.level(), player.blockPosition())) {
            message.append(Component.translatable("biome_based_crops.message.thermoregulator_nearby").withStyle(ChatFormatting.GREEN));
        }

        player.displayClientMessage(message, true);
    }

    private static CropClimate currentClimate(Level level, BlockPos pos) {
        return CropClimate.at(level.getBiome(pos), pos);
    }

    private static MutableComponent blockedMessage(CropProfile profile) {
        String key = ModList.get().isLoaded("toughasnails") && Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.get()
                ? "biome_based_crops.message.blocked_with_thermoregulator"
                : "biome_based_crops.message.blocked";
        return Component.translatable(key, profile.climateListComponent()).withStyle(ChatFormatting.RED);
    }
}
