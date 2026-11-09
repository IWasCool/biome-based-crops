package com.ourcreation.biomebasedcrops;

import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
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
            read(player, context.getClickedPos().relative(context.getClickedFace()));
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) read(serverPlayer, player.blockPosition());
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    private void read(ServerPlayer player, BlockPos pos) {
        player.getCooldowns().addCooldown(this, 10);
        float temperature = GreenhouseEnvironment.currentTemperature(player.level(), pos);
        float oxygen = GreenhouseEnvironment.oxygenPercent(player.level(), pos);
        player.displayClientMessage(Component.translatable("biome_based_crops.message.temperature_reading",
                String.format(Locale.ROOT, "%.1f", temperature), Math.round(oxygen) + "%")
                .withStyle(ChatFormatting.GRAY), true);
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("biome_based_crops.thermometer.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
