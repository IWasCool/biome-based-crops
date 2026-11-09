package com.ourcreation.biomebasedcrops;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public final class GreenhouseProbeItem extends Item {
    public GreenhouseProbeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            player.getCooldowns().addCooldown(this, 10);
            BlockPos pos = context.getClickedPos();
            if (player.isShiftKeyDown()) {
                sample(serverPlayer, context.getItemInHand(), pos);
            } else if (context.getLevel().getBlockEntity(pos) instanceof GreenhouseCoreBlockEntity core) {
                ResourceLocation biome = sampledBiome(context.getItemInHand());
                if (biome == null) {
                    player.displayClientMessage(Component.translatable("biome_based_crops.probe.sample_first"), true);
                } else if (context.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME).containsKey(biome)) {
                    core.setTargetBiome(biome);
                    GreenhouseNetwork.showSurvey(serverPlayer, pos);
                } else {
                    player.displayClientMessage(Component.translatable("biome_based_crops.probe.missing_biome", biome.toString()), true);
                }
            } else {
                GreenhouseNetwork.showSurvey(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            player.getCooldowns().addCooldown(this, 10);
            if (player.isShiftKeyDown()) {
                sample(serverPlayer, stack, player.blockPosition());
            } else {
                GreenhouseNetwork.showSurvey(serverPlayer, player.blockPosition());
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private static void sample(ServerPlayer player, ItemStack stack, BlockPos pos) {
        player.level().getBiome(pos).unwrapKey().ifPresent(key -> {
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString("SampledBiome", key.location().toString()));
            player.displayClientMessage(Component.translatable("biome_based_crops.probe.sampled", biomeName(key.location())), true);
            player.getCooldowns().addCooldown(stack.getItem(), 5);
        });
    }

    @Nullable
    public static ResourceLocation sampledBiome(ItemStack stack) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.contains("SampledBiome") ? ResourceLocation.tryParse(tag.getString("SampledBiome")) : null;
    }

    public static Component biomeName(ResourceLocation id) {
        return Component.translatableWithFallback("biome." + id.getNamespace() + "." + id.getPath().replace('/', '.'), id.toString());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("biome_based_crops.probe.tooltip").withStyle(ChatFormatting.GRAY));
        ResourceLocation biome = sampledBiome(stack);
        if (biome != null) {
            lines.add(Component.translatable("biome_based_crops.probe.sample_label", biomeName(biome)).withStyle(ChatFormatting.GREEN));
        }
    }
}
