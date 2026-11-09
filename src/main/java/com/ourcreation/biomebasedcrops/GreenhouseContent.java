package com.ourcreation.biomebasedcrops;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class GreenhouseContent {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, BiomeBasedCrops.MODID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, BiomeBasedCrops.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, BiomeBasedCrops.MODID);

    public static final DeferredHolder<Block, GreenhouseCoreBlock> CORE = BLOCKS.register("greenhouse_core",
            () -> new GreenhouseCoreBlock(BlockBehaviour.Properties.of().strength(2.5F).sound(SoundType.WOOD)));
    public static final DeferredHolder<Item, BlockItem> CORE_ITEM = ITEMS.register("greenhouse_core",
            () -> new BlockItem(CORE.get(), new Item.Properties()));
    public static final DeferredHolder<Item, GreenhouseProbeItem> PROBE = ITEMS.register("greenhouse_probe",
            () -> new GreenhouseProbeItem(new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<Item, FarmersGuideItem> GUIDE = ITEMS.register("farmer_field_guide",
            () -> new FarmersGuideItem(new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GreenhouseCoreBlockEntity>> CORE_ENTITY =
            BLOCK_ENTITIES.register("greenhouse_core", () -> BlockEntityType.Builder.of(GreenhouseCoreBlockEntity::new, CORE.get()).build(null));

    private GreenhouseContent() {
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        bus.addListener(GreenhouseContent::creativeTab);
        bus.addListener(GreenhouseNetwork::register);
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(CORE_ITEM.get());
            event.accept(PROBE.get());
            event.accept(GUIDE.get());
        }
    }
}
