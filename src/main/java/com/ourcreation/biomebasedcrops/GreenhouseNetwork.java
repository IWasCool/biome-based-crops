package com.ourcreation.biomebasedcrops;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class GreenhouseNetwork {
    private static final Map<ServerPlayer, PendingSurvey> PENDING_SURVEYS = new WeakHashMap<>();

    private GreenhouseNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("6").playToClient(ViewPayload.TYPE, ViewPayload.CODEC,
                (payload, context) -> com.ourcreation.biomebasedcrops.client.GreenhouseViews.open(payload));
    }

    public static void showGuide(ServerPlayer player) {
        PENDING_SURVEYS.remove(player);
        BlockPos position = player.blockPosition();
        GreenhouseRoomScanner.prioritize(player.level(), position);
        var context = GreenhouseEnvironment.inspect(player.level(), position);
        if (context.status().endsWith(".scanning")) {
            PENDING_SURVEYS.put(player, new PendingSurvey(position.immutable(), player.level().dimension().location(),
                    player.level().getGameTime() + 6000, false));
        }
        send(player, null, false, context.roomVolume(), context.floorArea());
    }

    public static void showSurvey(ServerPlayer player, BlockPos pos) {
        GreenhouseRoomScanner.prioritize(player.level(), pos);
        GreenhouseEnvironment.invalidateClimate();
        var inspection = GreenhouseEnvironment.inspect(player.level(), pos);
        if (inspection.status().endsWith(".scanning")) {
            PENDING_SURVEYS.put(player, new PendingSurvey(pos.immutable(), player.level().dimension().location(),
                    player.level().getGameTime() + 6000, true));
        } else PENDING_SURVEYS.remove(player);
        send(player, inspection);
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 10 != 0) return;
        var pending = PENDING_SURVEYS.get(player);
        if (pending == null) return;
        if (player.level().getGameTime() > pending.expires()
                || !player.level().dimension().location().equals(pending.dimension())) {
            PENDING_SURVEYS.remove(player);
            return;
        }
        var inspection = GreenhouseEnvironment.inspect(player.level(), pending.position());
        if (!inspection.status().endsWith(".scanning")) {
            PENDING_SURVEYS.remove(player);
            send(player, pending.survey() ? inspection : null, true, inspection.roomVolume(), inspection.floorArea());
        }
    }

    private static void send(ServerPlayer player, @Nullable GreenhouseEnvironment.Inspection inspection) {
        send(player, inspection, false);
    }

    private static void send(ServerPlayer player, @Nullable GreenhouseEnvironment.Inspection inspection, boolean updateOnly) {
        send(player, inspection, updateOnly, inspection == null ? 0 : inspection.roomVolume(), inspection == null ? 0 : inspection.floorArea());
    }

    private static void send(ServerPlayer player, @Nullable GreenhouseEnvironment.Inspection inspection, boolean updateOnly, int contextVolume, int contextFloorArea) {
        Map<ResourceLocation, GreenhouseRecipes.Recipe> recipes = new LinkedHashMap<>();
        player.registryAccess().registryOrThrow(Registries.BIOME).holders().forEach(biome ->
                recipes.put(biome.key().location(), GreenhouseRecipes.forServerBiome(biome)));
        PacketDistributor.sendToPlayer(player, new ViewPayload(recipes, inspection, updateOnly,
                GreenhouseSettings.materialReferenceVolume(), contextVolume, contextFloorArea));
    }

    private record PendingSurvey(BlockPos position, ResourceLocation dimension, long expires, boolean survey) {}

    public record ViewPayload(Map<ResourceLocation, GreenhouseRecipes.Recipe> recipes,
                              @Nullable GreenhouseEnvironment.Inspection inspection, boolean updateOnly,
                              int referenceVolume, int contextVolume, int contextFloorArea) implements CustomPacketPayload {
        public static final Type<ViewPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(BiomeBasedCrops.MODID, "greenhouse_view"));
        public static final StreamCodec<FriendlyByteBuf, ViewPayload> CODEC = StreamCodec.of(ViewPayload::write, ViewPayload::read);

        public ViewPayload {
            recipes = Map.copyOf(recipes);
            if (referenceVolume < 64 || referenceVolume > 1048576 || contextVolume < 0 || contextVolume > 1048576
                    || contextFloorArea < 0 || contextFloorArea > 1048576)
                throw new IllegalArgumentException("Invalid greenhouse volume context");
        }

        public ViewPayload(Map<ResourceLocation, GreenhouseRecipes.Recipe> recipes,
                           @Nullable GreenhouseEnvironment.Inspection inspection, boolean updateOnly, int referenceVolume, int contextVolume) {
            this(recipes, inspection, updateOnly, referenceVolume, contextVolume, inspection == null ? 0 : inspection.floorArea());
        }

        public ViewPayload(Map<ResourceLocation, GreenhouseRecipes.Recipe> recipes,
                           @Nullable GreenhouseEnvironment.Inspection inspection) {
            this(recipes, inspection, false);
        }

        public ViewPayload(Map<ResourceLocation, GreenhouseRecipes.Recipe> recipes,
                           @Nullable GreenhouseEnvironment.Inspection inspection, boolean updateOnly) {
            this(recipes, inspection, updateOnly, GreenhouseSettings.materialReferenceVolume(),
                    inspection == null ? 0 : inspection.roomVolume(), inspection == null ? 0 : inspection.floorArea());
        }

        private static void write(FriendlyByteBuf buf, ViewPayload payload) {
            buf.writeVarInt(payload.recipes.size());
            payload.recipes.forEach((id, recipe) -> {
                buf.writeResourceLocation(id);
                buf.writeEnum(recipe.climate());
                buf.writeVarInt(recipe.requirements().size());
                recipe.requirements().forEach(requirement -> {
                    buf.writeResourceLocation(requirement.block());
                    buf.writeVarInt(requirement.count());
                    buf.writeVarInt(requirement.floorDivisor());
                    buf.writeVarInt(requirement.minHeight());
                });
                writeBlocks(buf, recipe.floor().blocks());
                buf.writeVarInt(recipe.floor().coveragePercent());
            });
            buf.writeBoolean(payload.inspection != null);
            if (payload.inspection != null) {
                var report = payload.inspection;
                buf.writeBoolean(report.enclosed());
                buf.writeNullable(report.targetBiome(), FriendlyByteBuf::writeResourceLocation);
                buf.writeEnum(report.climate());
                buf.writeVarInt(report.heat());
                buf.writeVarInt(report.cooling());
                buf.writeVarInt(report.glass());
                buf.writeVarInt(report.specimens());
                buf.writeVarInt(report.requiredSpecimens());
                buf.writeUtf(report.status(), 128);
                writeBlocks(buf, report.specimenBlocks());
                buf.writeVarInt(report.requirements().size());
                report.requirements().forEach(requirement -> {
                    buf.writeResourceLocation(requirement.block());
                    buf.writeVarInt(requirement.found());
                    buf.writeVarInt(requirement.required());
                });
                buf.writeEnum(report.baseClimate());
                buf.writeVarInt(report.roomVolume());
                buf.writeVarInt(report.floorArea());
                writeBlocks(buf, report.floor().blocks());
                buf.writeVarInt(report.floor().found());
                buf.writeVarInt(report.floor().required());
                buf.writeVarInt(report.floor().total());
            }
            buf.writeBoolean(payload.updateOnly);
            buf.writeVarInt(payload.referenceVolume);
            buf.writeVarInt(payload.contextVolume);
            buf.writeVarInt(payload.contextFloorArea);
        }

        private static ViewPayload read(FriendlyByteBuf buf) {
            Map<ResourceLocation, GreenhouseRecipes.Recipe> recipes = new LinkedHashMap<>();
            int size = bounded(buf.readVarInt(), 4096);
            for (int i = 0; i < size; i++) {
                ResourceLocation id = buf.readResourceLocation();
                CropClimate climate = buf.readEnum(CropClimate.class);
                int count = bounded(buf.readVarInt(), 32);
                List<GreenhouseRecipes.MaterialRequirement> requirements = new ArrayList<>();
                for (int j = 0; j < count; j++) {
                    requirements.add(new GreenhouseRecipes.MaterialRequirement(buf.readResourceLocation(), bounded(buf.readVarInt(), 64),
                            bounded(buf.readVarInt(), 256), bounded(buf.readVarInt(), 2)));
                }
                var floor = new GreenhouseRecipes.FloorRequirement(readBlocks(buf), bounded(buf.readVarInt(), 100));
                recipes.put(id, new GreenhouseRecipes.Recipe(climate,
                        requirements.stream().map(GreenhouseRecipes.MaterialRequirement::block).toList(),
                        requirements.stream().mapToInt(GreenhouseRecipes.MaterialRequirement::count).sum(), requirements, floor));
            }
            GreenhouseEnvironment.Inspection inspection = null;
            if (buf.readBoolean()) {
                boolean enclosed = buf.readBoolean();
                ResourceLocation target = buf.readNullable(FriendlyByteBuf::readResourceLocation);
                CropClimate climate = buf.readEnum(CropClimate.class);
                int heat = bounded(buf.readVarInt(), 16384);
                int cooling = bounded(buf.readVarInt(), 16384);
                int glass = bounded(buf.readVarInt(), 8_000_000);
                int specimens = bounded(buf.readVarInt(), 33_554_432);
                int required = bounded(buf.readVarInt(), 33_554_432);
                String status = buf.readUtf(128);
                List<ResourceLocation> blocks = readBlocks(buf);
                int progressCount = bounded(buf.readVarInt(), 32);
                List<GreenhouseEnvironment.RequirementProgress> progress = new ArrayList<>();
                for (int j = 0; j < progressCount; j++) {
                    progress.add(new GreenhouseEnvironment.RequirementProgress(buf.readResourceLocation(),
                            bounded(buf.readVarInt(), 8_000_000), bounded(buf.readVarInt(), 1_048_576)));
                }
                CropClimate base = buf.readEnum(CropClimate.class);
                int volume = bounded(buf.readVarInt(), 1_048_576), area = bounded(buf.readVarInt(), 1_048_576);
                var floor = new GreenhouseEnvironment.FloorProgress(readBlocks(buf), bounded(buf.readVarInt(), 1_048_576),
                        bounded(buf.readVarInt(), 1_048_576), bounded(buf.readVarInt(), 1_048_576));
                inspection = new GreenhouseEnvironment.Inspection(enclosed, target, climate, heat, cooling,
                        glass, specimens, required, status, blocks, progress, base, volume, area, floor);
            }
            return new ViewPayload(recipes, inspection, buf.readBoolean(), bounded(buf.readVarInt(), 1_048_576),
                    bounded(buf.readVarInt(), 1_048_576), bounded(buf.readVarInt(), 1_048_576));
        }

        private static void writeBlocks(FriendlyByteBuf buf, List<ResourceLocation> blocks) {
            buf.writeVarInt(blocks.size());
            blocks.forEach(buf::writeResourceLocation);
        }

        private static List<ResourceLocation> readBlocks(FriendlyByteBuf buf) {
            int size = bounded(buf.readVarInt(), 32);
            List<ResourceLocation> blocks = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                blocks.add(buf.readResourceLocation());
            }
            return List.copyOf(blocks);
        }

        private static int bounded(int value, int max) {
            if (value < 0 || value > max) {
                throw new IllegalArgumentException("Invalid greenhouse catalog size");
            }
            return value;
        }

        @Override
        public Type<ViewPayload> type() {
            return TYPE;
        }
    }
}
