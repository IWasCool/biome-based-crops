package com.ourcreation.biomebasedcrops;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public final class ThermometerItem extends Item {
    public ThermometerItem(Properties properties) { super(properties); }

    @Override public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() instanceof ServerPlayer player)
            read(player);
        return context.getLevel().isClientSide() ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
    }

    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) read(serverPlayer);
        return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
    }

    private void read(ServerPlayer player) {
        player.getCooldowns().addCooldown(new ItemStack(GreenhouseContent.THERMOMETER.get()), 10);
        showReading(player);
    }

    public static void showReading(ServerPlayer player) {
        showReading(player, player.blockPosition());
    }

    public static void showReading(ServerPlayer player, BlockPos pos) {
        CropClimate climate = GreenhouseEnvironment.currentClimate(player.level(), pos);
        Component biome = player.level().getBiome(pos).unwrapKey()
                .map(key -> BiomeSelector.parse(key.location().toString()).orElseThrow().component())
                .orElseGet(() -> Component.translatable("biome_based_crops.biome.unknown"));
        player.displayClientMessage(Component.translatable("biome_based_crops.message.climate_reading",
                climate.component(), biome)
                .withStyle(ChatFormatting.GRAY), true);
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> lines, TooltipFlag flag) {
        lines.accept(usageTooltip());
    }

    static Component usageTooltip() {
        return Component.translatable("biome_based_crops.thermometer.tooltip").withStyle(ChatFormatting.GRAY);
    }
}
