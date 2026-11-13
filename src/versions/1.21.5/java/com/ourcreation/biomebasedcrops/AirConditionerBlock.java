package com.ourcreation.biomebasedcrops;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

public final class AirConditionerBlock extends BaseEntityBlock {
    public static final MapCodec<AirConditionerBlock> CODEC = simpleCodec(AirConditionerBlock::new);
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final EnumProperty<CropClimate> CLIMATE = EnumProperty.create("climate", CropClimate.class);

    public AirConditionerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ACTIVE, false).setValue(CLIMATE, CropClimate.NEUTRAL));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new AirConditionerBlockEntity(pos, state); }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, ACTIVE, CLIMATE); }
    @Override protected BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState state, Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(FACING))); }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof AirConditionerBlockEntity unit) {
            serverPlayer.openMenu(unit, pos);
        }
        return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
    }

    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, GreenhouseContent.AIR_CONDITIONER_ENTITY.get(),
                level.isClientSide() ? AirConditionerBlockEntity::clientTick : AirConditionerBlockEntity::serverTick);
    }

    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, net.minecraft.world.level.redstone.Orientation orientation, boolean moving) {
        if (!level.isClientSide()) GreenhouseEnvironment.invalidateClimate();
        super.neighborChanged(state, level, pos, block, orientation, moving);
    }

    @Override protected void affectNeighborsAfterRemoval(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, boolean moving) {
        GreenhouseEnvironment.invalidateClimate();
        super.affectNeighborsAfterRemoval(state, level, pos, moving);
    }

    public static void emitAirflow(Level level, BlockPos pos, BlockState state) {
        RandomSource random = level.random;
        Direction face = state.getValue(FACING);
        // Cycle the fan's thirds across ticks: one faint wisp, rather than three smoke puffs.
        int sector = (int) Math.floorMod(level.getGameTime(), 3L);
        double angle = -Math.PI / 2 + (sector + random.nextDouble()) * Math.PI / 3;
        double forward = Math.cos(angle), sideways = Math.sin(angle);
        double speed = 0.12 + random.nextDouble() * 0.06;
        double across = (random.nextDouble() - 0.5) * 0.45;
        double x = pos.getX() + 0.5 + face.getStepX() * 0.53 + face.getStepZ() * across;
        double z = pos.getZ() + 0.5 + face.getStepZ() * 0.53 - face.getStepX() * across;
        level.addAlwaysVisibleParticle(GreenhouseContent.AIRFLOW_PARTICLE.get(), true,
                x, pos.getY() + 0.55 + random.nextDouble() * 0.15, z,
                (face.getStepX() * forward + face.getStepZ() * sideways) * speed, 0.002,
                (face.getStepZ() * forward - face.getStepX() * sideways) * speed);
    }

    public static void emitIntakeAirflow(Level level, BlockPos pos, BlockState state) {
        // A sparse rear wisp makes the intake visible without competing with the outlet fan.
        if (Math.floorMod(level.getGameTime() + pos.asLong(), 4L) != 0) return;
        Direction rear = state.getValue(FACING).getOpposite();
        BlockPos intake = pos.relative(rear);
        if (!level.hasChunkAt(intake) || !level.getFluidState(intake).isEmpty()
                || !level.getBlockState(intake).getCollisionShape(level, intake).isEmpty()) return;

        RandomSource random = level.random;
        double distance = 0.85 + random.nextDouble() * 0.2;
        double across = (random.nextDouble() - 0.5) * 0.44;
        double height = 0.34 + random.nextDouble() * 0.4;
        double x = pos.getX() + 0.5 + rear.getStepX() * distance + rear.getStepZ() * across;
        double z = pos.getZ() + 0.5 + rear.getStepZ() * distance - rear.getStepX() * across;
        // Converge toward the vent. Particle collision stops the wisp at the machine's face.
        double targetX = pos.getX() + 0.5 + rear.getStepX() * 0.53;
        double targetZ = pos.getZ() + 0.5 + rear.getStepZ() * 0.53;
        level.addParticle(GreenhouseContent.AIRFLOW_PARTICLE.get(),
                x, pos.getY() + height, z,
                (targetX - x) * 0.07, (0.55 - height) * 0.07, (targetZ - z) * 0.07);
    }
}
