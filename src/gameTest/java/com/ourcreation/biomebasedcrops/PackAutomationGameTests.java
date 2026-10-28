package com.ourcreation.biomebasedcrops;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Calls the installed automation implementations without linking them into the release jar. */
@GameTestHolder(BiomeBasedCrops.MODID)
@PrefixGameTestTemplate(false)
public final class PackAutomationGameTests {
    private static final BlockPos PLANT = new BlockPos(6, 2, 6);

    private PackAutomationGameTests() {
    }

    @GameTest(template = "empty", skyAccess = true, batch = "pack_automation")
    public static void createTreeFertilizerChecksRealBiomeAndPreservesBlockedItems(GameTestHelper helper) {
        if (!hasMod(helper, "create")) {
            return;
        }
        withConfig(helper, () -> {
            ServerLevel level = helper.getLevel();
            BlockPos pos = plant(helper, Blocks.ACACIA_SAPLING.defaultBlockState(), Blocks.DIRT.defaultBlockState());
            assertWrongBiome(helper, pos);
            Player player = FakePlayerFactory.getMinecraft(level);
            ItemStack previous = player.getMainHandItem();
            boolean creative = player.getAbilities().instabuild;
            try (StageScope ignored = StageScope.unlockPlayer(player)) {
                ItemStack fertilizer = new ItemStack(BuiltInRegistries.ITEM.get(
                        ResourceLocation.fromNamespaceAndPath("create", "tree_fertilizer")), 3);
                helper.assertFalse(fertilizer.isEmpty(), "Create tree fertilizer was not registered");
                player.getAbilities().instabuild = false;
                player.setItemInHand(InteractionHand.MAIN_HAND, fertilizer);
                helper.assertValueEqual(fertilizer.useOn(context(player, pos)), InteractionResult.FAIL,
                        "wrong-biome Create fertilizer result");
                helper.assertValueEqual(fertilizer.getCount(), 3, "blocked Create fertilizer count");
                helper.assertTrue(level.getBlockState(pos).is(Blocks.ACACIA_SAPLING), "Blocked fertilizer replaced the sapling");

                // The optional item hook must preserve Create's PASS result for unrelated targets.
                level.setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
                helper.assertValueEqual(fertilizer.useOn(context(player, pos)), InteractionResult.PASS,
                        "Create fertilizer result on a non-sapling");
                helper.assertValueEqual(fertilizer.getCount(), 3, "fertilizer count after unrelated target");

                level.setBlock(pos, Blocks.ACACIA_SAPLING.defaultBlockState(), Block.UPDATE_ALL);
                Config.BLOCK_BONEMEAL_GROWTH.set(false);
                BlockState beforeAllowed = level.getBlockState(pos);
                InteractionResult allowed = fertilizer.useOn(context(player, pos));
                helper.assertTrue(allowed.consumesAction(),
                        "Create fertilizer stayed blocked after its growth restriction was disabled: result=" + allowed
                                + ", before=" + beforeAllowed + ", after=" + level.getBlockState(pos)
                                + ", helperAllowed=" + CommonEvents.canGrowWithFertilizer(level, pos, beforeAllowed)
                                + ", hand=" + player.getMainHandItem() + ", stack=" + fertilizer);
                helper.assertValueEqual(fertilizer.getCount(), 2, "allowed Create fertilizer count");
                helper.assertFalse(level.getBlockState(pos).is(Blocks.ACACIA_SAPLING),
                        "Allowed Create fertilizer did not grow a tree");
            } finally {
                player.setItemInHand(InteractionHand.MAIN_HAND, previous);
                player.getAbilities().instabuild = creative;
            }
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "pack_automation")
    public static void createDeployerBonemealUsesTheExistingGrowthGuard(GameTestHelper helper) {
        if (!hasMod(helper, "create")) {
            return;
        }
        withConfig(helper, () -> {
            ServerLevel level = helper.getLevel();
            BlockPos pos = plant(helper, Blocks.WHEAT.defaultBlockState(),
                    Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
            assertWrongBiome(helper, pos);
            Class<?> deployerPlayer = Class.forName("com.simibubi.create.content.kinetics.deployer.DeployerFakePlayer");
            Player player = (Player) deployerPlayer.getConstructor(ServerLevel.class, UUID.class).newInstance(level, null);
            player.getAbilities().instabuild = false;
            try (StageScope ignored = StageScope.unlockPlayer(player)) {
                ItemStack meal = new ItemStack(Items.BONE_MEAL, 3);
                player.setItemInHand(InteractionHand.MAIN_HAND, meal);
                activateDeployer(deployerPlayer, player, pos);
                helper.assertValueEqual(level.getBlockState(pos).getValue(CropBlock.AGE), 0, "blocked deployer crop age");
                helper.assertValueEqual(player.getMainHandItem().getCount(), 3, "blocked deployer bonemeal count");

                Config.BLOCK_BONEMEAL_GROWTH.set(false);
                activateDeployer(deployerPlayer, player, pos);
                helper.assertTrue(level.getBlockState(pos).getValue(CropBlock.AGE) > 0,
                        "Deployer bonemeal did not grow wheat after disabling its restriction");
                helper.assertValueEqual(player.getMainHandItem().getCount(), 2, "allowed deployer bonemeal count");
            }
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "pack_automation")
    public static void sliceAndDiceFertilizerActionChecksEachPlantBeforeGrowing(GameTestHelper helper) {
        if (!hasMod(helper, "sliceanddice")) {
            return;
        }
        withConfig(helper, () -> {
            ServerLevel level = helper.getLevel();
            BlockPos pos = plant(helper, Blocks.WHEAT.defaultBlockState(),
                    Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
            assertWrongBiome(helper, pos);
            consumeSprinklerFertilizer(level, pos);
            helper.assertValueEqual(level.getBlockState(pos).getValue(CropBlock.AGE), 0, "blocked sprinkler crop age");

            Config.BLOCK_NATURAL_GROWTH.set(false);
            consumeSprinklerFertilizer(level, pos);
            helper.assertValueEqual(level.getBlockState(pos).getValue(CropBlock.AGE), 0,
                    "sprinkler crop age with only natural restrictions disabled");

            Config.BLOCK_BONEMEAL_GROWTH.set(false);
            consumeSprinklerFertilizer(level, pos);
            helper.assertTrue(level.getBlockState(pos).getValue(CropBlock.AGE) > 0,
                    "Sprinkler fertilizer did not grow wheat after disabling its restriction");
        });
    }

    private static void activateDeployer(Class<?> playerType, Player player, BlockPos pos) throws ReflectiveOperationException {
        Class<?> modeType = Class.forName("com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity$Mode");
        Class<?> handler = Class.forName("com.simibubi.create.content.kinetics.deployer.DeployerHandler");
        Method activate = handler.getDeclaredMethod("activate", playerType, Vec3.class, BlockPos.class, Vec3.class, modeType);
        activate.setAccessible(true);
        Field useMode = modeType.getDeclaredField("USE");
        useMode.setAccessible(true);
        activate.invoke(null, player, pos.getCenter().add(0, 0, -2), pos, new Vec3(0, 0, 1), useMode.get(null));
    }

    private static void consumeSprinklerFertilizer(ServerLevel level, BlockPos pos) throws ReflectiveOperationException {
        Class<?> contextType = Class.forName("com.possible_triangle.sliceanddice.api.sprinkler.SprinkleContext");
        Class<?> functionType = Class.forName("kotlin.jvm.functions.Function1");
        Method invoke = functionType.getMethod("invoke", Object.class);
        Object context = Proxy.newProxyInstance(contextType.getClassLoader(), new Class<?>[]{contextType}, (proxy, method, args) -> {
            return switch (method.getName()) {
                case "getLevel" -> level;
                case "getRandom" -> new FertilizerSuccessRandom();
                case "forEachBlock" -> {
                    invoke.invoke(args[0], pos);
                    yield null;
                }
                case "toString" -> "BiomeBasedCrops fertilizer integration context";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException("Unexpected sprinkler context method " + method.getName());
            };
        });
        Class<?> actionType = Class.forName("com.possible_triangle.sliceanddice.block.sprinkler.actions.FertilizerAction$Type");
        Class<?> configType = Class.forName("com.possible_triangle.sliceanddice.block.sprinkler.actions.FertilizerAction");
        Object action = actionType.getField("INSTANCE").get(null);
        Object config = configType.getField("INSTANCE").get(null);
        actionType.getMethod("consume", contextType, configType).invoke(action, context, config);
    }

    private static UseOnContext context(Player player, BlockPos pos) {
        return new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(pos.getCenter(), Direction.UP, pos, false));
    }

    private static BlockPos plant(GameTestHelper helper, BlockState plant, BlockState soil) {
        BlockPos pos = helper.absolutePos(PLANT);
        helper.getLevel().setBlock(pos.below(), soil, Block.UPDATE_ALL);
        helper.getLevel().setBlock(pos, plant, Block.UPDATE_ALL);
        return pos;
    }

    private static void assertWrongBiome(GameTestHelper helper, BlockPos pos) {
        helper.assertFalse(CropMatcher.canGrowAt(helper.getLevel(), pos, helper.getLevel().getBlockState(pos)),
                "The pack biome permits this plant, so the test cannot exercise its growth restriction");
    }

    private static boolean hasMod(GameTestHelper helper, String id) {
        if (ModList.get().isLoaded(id)) {
            return true;
        }
        BiomeBasedCrops.LOGGER.info("Skipping optional automation GameTest: {} not installed", id);
        helper.succeed();
        return false;
    }

    private static void withConfig(GameTestHelper helper, CheckedTest test) {
        boolean natural = Config.BLOCK_NATURAL_GROWTH.get();
        boolean bonemeal = Config.BLOCK_BONEMEAL_GROWTH.get();
        boolean crops = Config.ENABLE_CROP_BIOME_DEPENDENCIES.get();
        boolean saplings = Config.ENABLE_SAPLING_BIOME_DEPENDENCIES.get();
        boolean compatibility = Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.get();
        try {
            Config.BLOCK_NATURAL_GROWTH.set(true);
            Config.BLOCK_BONEMEAL_GROWTH.set(true);
            Config.ENABLE_CROP_BIOME_DEPENDENCIES.set(true);
            Config.ENABLE_SAPLING_BIOME_DEPENDENCIES.set(true);
            Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.set(false);
            CropMatcher.clearCaches();
            CropClimateRules.clearCache();
            helper.setDayTime(1000);
            helper.setBiome(Biomes.SNOWY_PLAINS);
            test.run();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Installed automation API did not match the tested integration", e);
        } finally {
            Config.BLOCK_NATURAL_GROWTH.set(natural);
            Config.BLOCK_BONEMEAL_GROWTH.set(bonemeal);
            Config.ENABLE_CROP_BIOME_DEPENDENCIES.set(crops);
            Config.ENABLE_SAPLING_BIOME_DEPENDENCIES.set(saplings);
            Config.ENABLE_THERMOREGULATOR_COMPATIBILITY.set(compatibility);
            CropMatcher.clearCaches();
            CropClimateRules.clearCache();
        }
        helper.succeed();
    }

    @FunctionalInterface
    private interface CheckedTest {
        void run() throws ReflectiveOperationException;
    }

    /** Keep the pack's progression checks active while testing an unlocked farmer. */
    private static final class StageScope implements AutoCloseable {
        private final Object holder;
        private final Method read;
        private final Method add;
        private final Method remove;
        private final Set<String> previous;

        private StageScope(Object holder, Method read, Method add, Method remove, Set<String> previous) {
            this.holder = holder;
            this.read = read;
            this.add = add;
            this.remove = remove;
            this.previous = previous;
        }

        private static StageScope unlockPlayer(Player player) throws ReflectiveOperationException {
            if (!ModList.get().isLoaded("astages")) {
                return new StageScope(null, null, null, null, Set.of());
            }
            Class<?> holderType = Class.forName("com.alessandro.astages.api.holder.AHolder");
            Class<?> stages = Class.forName("com.alessandro.astages.api.util.AStagesUtils");
            Object holder = holderType.getMethod("player", Player.class).invoke(null, player);
            Method read = stages.getMethod("getStages", holderType);
            Method add = stages.getMethod("addStages", holderType, Set.class, boolean.class, boolean.class, boolean.class);
            Method remove = stages.getMethod("removeStages", holderType, Set.class, boolean.class, boolean.class, boolean.class);
            Set<String> previous = stageIds(read.invoke(null, holder));
            StageScope scope = new StageScope(holder, read, add, remove, previous);
            try {
                // The installed API uses its registered stage catalog, including the pack's own stages.
                stages.getMethod("addAllStages", holderType, boolean.class, boolean.class, boolean.class)
                        .invoke(null, holder, false, false, false);
            } catch (ReflectiveOperationException failure) {
                scope.close();
                throw failure;
            }
            return scope;
        }

        @Override
        public void close() throws ReflectiveOperationException {
            if (holder == null) {
                return;
            }
            Set<String> added = stageIds(read.invoke(null, holder));
            added.removeAll(previous);
            if (!added.isEmpty()) {
                remove.invoke(null, holder, added, false, false, false);
            }
            Set<String> missing = new HashSet<>(previous);
            missing.removeAll(stageIds(read.invoke(null, holder)));
            if (!missing.isEmpty()) {
                add.invoke(null, holder, missing, false, false, false);
            }
            if (!stageIds(read.invoke(null, holder)).equals(previous)) {
                throw new IllegalStateException("The automation test did not restore the fake player's AStages progression");
            }
        }

        private static Set<String> stageIds(Object result) {
            Set<String> stages = new HashSet<>();
            for (Object stage : (Set<?>) result) {
                stages.add((String) stage);
            }
            return stages;
        }
    }

    private static final class FertilizerSuccessRandom extends LegacyRandomSource {
        private FertilizerSuccessRandom() {
            super(0L);
        }

        @Override
        public int nextInt(int bound) {
            return bound - 1;
        }
    }
}
