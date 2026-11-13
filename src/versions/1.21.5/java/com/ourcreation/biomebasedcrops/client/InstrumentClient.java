package com.ourcreation.biomebasedcrops.client;

import java.util.HashMap;
import java.util.Map;
import com.ourcreation.biomebasedcrops.AirConditionerBlock;
import com.ourcreation.biomebasedcrops.AirConditionerBlockEntity;
import com.ourcreation.biomebasedcrops.GreenhouseContent;
import com.ourcreation.biomebasedcrops.GreenhouseEnvironment;
import com.ourcreation.biomebasedcrops.CropClimate;
import net.minecraft.client.Minecraft;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RegisterRangeSelectItemModelPropertyEvent;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.event.ClientTickEvent;

public final class InstrumentClient {
    private static final Map<BlockPos, CompressorSound> SOUNDS = new HashMap<>();
    private InstrumentClient() { }

    public static void registerProperties(RegisterRangeSelectItemModelPropertyEvent event) {
        event.register(ResourceLocation.fromNamespaceAndPath("biome_based_crops", "temperature"), ClimateProperty.MAP_CODEC);
    }

    public record ClimateProperty() implements RangeSelectItemModelProperty {
        public static final MapCodec<ClimateProperty> MAP_CODEC = MapCodec.unit(new ClimateProperty());

        @Override public float get(ItemStack stack, ClientLevel level, LivingEntity entity, int seed) {
            var player = Minecraft.getInstance().player;
            var world = level != null ? level : player == null ? null : player.level();
            var holder = entity != null ? entity : player;
            CropClimate climate = world == null || holder == null ? CropClimate.NEUTRAL
                    : GreenhouseEnvironment.currentClimate(world, holder.blockPosition());
            var sample = stack.get(DataComponents.CUSTOM_DATA);
            if (sample != null && sample.copyTag().contains("biome_based_crops:climate"))
                climate = CropClimate.byId(sample.copyTag().getStringOr("biome_based_crops:climate", "")).orElse(climate);
            return climate.ordinal() / (float) (CropClimate.values().length - 1);
        }

        @Override public MapCodec<ClimateProperty> type() { return MAP_CODEC; }
    }

    public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) { clear(); return; }
        if (mc.level.getGameTime() % 10 != 0) return;
        SOUNDS.values().removeIf(sound -> sound.isStopped() || !mc.getSoundManager().isActive(sound));
        BlockPos player = mc.player.blockPosition();
        for (int x = (player.getX() - 16) >> 4; x <= (player.getX() + 16) >> 4; x++) {
            for (int z = (player.getZ() - 16) >> 4; z <= (player.getZ() + 16) >> 4; z++) {
                var chunk = mc.level.getChunkSource().getChunkNow(x, z);
                if (chunk == null) continue;
                for (var entity : chunk.getBlockEntities().values()) {
                    if (!(entity instanceof AirConditionerBlockEntity unit) || !unit.hasPaidAirflow()
                            || unit.getBlockPos().distSqr(player) > 256 || SOUNDS.containsKey(unit.getBlockPos())) continue;
                    var sound = new CompressorSound(unit);
                    SOUNDS.put(unit.getBlockPos(), sound);
                    mc.getSoundManager().play(sound);
                }
            }
        }
    }

    public static void clear() {
        var manager = Minecraft.getInstance().getSoundManager();
        SOUNDS.values().forEach(manager::stop);
        SOUNDS.clear();
    }

    private static final class CompressorSound extends AbstractTickableSoundInstance {
        private final AirConditionerBlockEntity unit;
        CompressorSound(AirConditionerBlockEntity unit) {
            super(GreenhouseContent.COMPRESSOR_SOUND.get(), SoundSource.BLOCKS, RandomSource.create());
            this.unit = unit;
            looping = true;
            delay = 0;
            volume = 0.12F;
            pitch = 1.0F;
            x = unit.getBlockPos().getX() + 0.5;
            y = unit.getBlockPos().getY() + 0.5;
            z = unit.getBlockPos().getZ() + 0.5;
        }
        @Override public void tick() {
            var mc = Minecraft.getInstance();
            if (unit.getLevel() != mc.level || !unit.hasPaidAirflow() || mc.player == null
                    || mc.player.distanceToSqr(x, y, z) > 400) stop();
        }
    }
}
