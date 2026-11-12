package com.ourcreation.biomebasedcrops;

import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Share the instrument reading without replacing external regulator menus or gauge controls. */
public final class TemperatureInstrumentCompatibility {
    private static final Set<String> THERMOMETERS = Set.of("toughasnails:thermometer", "northstar:thermometer");
    private static final Set<String> DEVICES = Set.of("toughasnails:thermoregulator", "toughasnails:temperature_gauge", "northstar:temperature_regulator");
    private TemperatureInstrumentCompatibility() { }

    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (!thermometer(event.getItemStack())) return;
        read(event);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        event.setCanceled(true);
    }

    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        if (thermometer(event.getItemStack())) {
            read(event);
            event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
            event.setCanceled(true);
        } else if (DEVICES.contains(BuiltInRegistries.BLOCK.getKey(event.getLevel().getBlockState(event.getPos()).getBlock()).toString())
                && event.getEntity() instanceof ServerPlayer player) {
            ThermometerItem.showReading(player, event.getPos());
        }
    }

    private static boolean thermometer(ItemStack stack) {
        return stack.getItem() instanceof ThermometerItem
                || THERMOMETERS.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }

    private static void read(PlayerInteractEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !player.getCooldowns().isOnCooldown(event.getItemStack().getItem())) {
            player.getCooldowns().addCooldown(event.getItemStack().getItem(), 10);
            ThermometerItem.showReading(player);
        }
    }
}
