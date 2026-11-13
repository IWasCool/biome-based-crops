package com.ourcreation.biomebasedcrops;

import net.minecraft.core.registries.Registries;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class GreenhouseContent {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, BiomeBasedCrops.MODID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, BiomeBasedCrops.MODID);
    private static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, BiomeBasedCrops.MODID);
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> AIRFLOW_PARTICLE = PARTICLES.register("airflow",
            () -> new SimpleParticleType(false));
    public static final DeferredHolder<SoundEvent, SoundEvent> COMPRESSOR_SOUND = SOUNDS.register("compressor",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(BiomeBasedCrops.MODID, "compressor")));
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, BiomeBasedCrops.MODID);
    private static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BiomeBasedCrops.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, BiomeBasedCrops.MODID);

    public static final DeferredHolder<Item, FarmersGuideItem> GUIDE = ITEMS.register("farmer_field_guide",
            () -> new FarmersGuideItem(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(BiomeBasedCrops.MODID, "farmer_field_guide"))).stacksTo(1)));
    public static final DeferredHolder<Item, ThermometerItem> THERMOMETER = ITEMS.register("thermometer",
            () -> new ThermometerItem(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(BiomeBasedCrops.MODID, "thermometer"))).stacksTo(1)));
    public static final DeferredHolder<Block, AirConditionerBlock> AIR_CONDITIONER = BLOCKS.register("air_conditioner",
            () -> new AirConditionerBlock(BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(BiomeBasedCrops.MODID, "air_conditioner"))).strength(3.5F).sound(SoundType.METAL)));
    public static final DeferredHolder<Item, BlockItem> AIR_CONDITIONER_ITEM = ITEMS.register("air_conditioner",
            () -> new BlockItem(AIR_CONDITIONER.get(), new Item.Properties().useBlockDescriptionPrefix().setId(ResourceKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(BiomeBasedCrops.MODID, "air_conditioner")))));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AirConditionerBlockEntity>> AIR_CONDITIONER_ENTITY =
            BLOCK_ENTITIES.register("air_conditioner", () -> new BlockEntityType<>(AirConditionerBlockEntity::new, AIR_CONDITIONER.get()));
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CREATIVE_TAB = CREATIVE_TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.biome_based_crops"))
                    .icon(() -> new ItemStack(AIR_CONDITIONER_ITEM.get()))
                    // NeoForge also places the first registered mod tab after the vanilla tabs.
                    // Match that constraint so standalone launches cannot form an ordering cycle.
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .displayItems((parameters, output) -> ITEMS.getEntries().forEach(item -> output.accept(item.get())))
                    .build());

    private GreenhouseContent() {
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        SOUNDS.register(bus);
        PARTICLES.register(bus);
        ITEMS.register(bus);
        CREATIVE_TABS.register(bus);
        BLOCK_ENTITIES.register(bus);
        bus.addListener(GreenhouseNetwork::register);
        bus.addListener(GreenhouseContent::capabilities);
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, AIR_CONDITIONER_ENTITY.get(),
                (unit, side) -> unit.energyStorage());
    }
}
