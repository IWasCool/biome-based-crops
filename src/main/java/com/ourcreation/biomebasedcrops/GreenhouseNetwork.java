package com.ourcreation.biomebasedcrops;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class GreenhouseNetwork {
    private GreenhouseNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(ViewPayload.TYPE, ViewPayload.CODEC,
                (payload, context) -> com.ourcreation.biomebasedcrops.client.GreenhouseViews.open(payload));
    }

    public static void showGuide(ServerPlayer player) {
        send(player, null);
    }

    public static void showSurvey(ServerPlayer player, BlockPos pos) {
        GreenhouseEnvironment.clearCache();
        send(player, GreenhouseEnvironment.inspect(player.level(), pos));
    }

    private static void send(ServerPlayer player, @Nullable GreenhouseEnvironment.Inspection inspection) {
        Map<ResourceLocation, GreenhouseRecipes.Recipe> recipes = new LinkedHashMap<>();
        player.registryAccess().registryOrThrow(Registries.BIOME).holders().forEach(biome ->
                recipes.put(biome.key().location(), GreenhouseRecipes.forServerBiome(biome)));
        PacketDistributor.sendToPlayer(player, new ViewPayload(recipes, inspection));
    }

    public record ViewPayload(Map<ResourceLocation, GreenhouseRecipes.Recipe> recipes,
                              @Nullable GreenhouseEnvironment.Inspection inspection) implements CustomPacketPayload {
        public static final Type<ViewPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(BiomeBasedCrops.MODID, "greenhouse_view"));
        public static final StreamCodec<FriendlyByteBuf, ViewPayload> CODEC = StreamCodec.of(ViewPayload::write, ViewPayload::read);

        public ViewPayload {
            recipes = Map.copyOf(recipes);
        }

        private static void write(FriendlyByteBuf buf, ViewPayload payload) {
            buf.writeVarInt(payload.recipes.size());
            payload.recipes.forEach((id, recipe) -> {
                buf.writeResourceLocation(id);
                buf.writeEnum(recipe.climate());
                writeBlocks(buf, recipe.specimens());
                buf.writeVarInt(recipe.count());
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
            }
        }

        private static ViewPayload read(FriendlyByteBuf buf) {
            Map<ResourceLocation, GreenhouseRecipes.Recipe> recipes = new LinkedHashMap<>();
            int size = bounded(buf.readVarInt(), 4096);
            for (int i = 0; i < size; i++) {
                ResourceLocation id = buf.readResourceLocation();
                CropClimate climate = buf.readEnum(CropClimate.class);
                List<ResourceLocation> blocks = readBlocks(buf);
                recipes.put(id, new GreenhouseRecipes.Recipe(climate, blocks, bounded(buf.readVarInt(), 256)));
            }
            GreenhouseEnvironment.Inspection inspection = null;
            if (buf.readBoolean()) {
                inspection = new GreenhouseEnvironment.Inspection(buf.readBoolean(), buf.readNullable(FriendlyByteBuf::readResourceLocation),
                        buf.readEnum(CropClimate.class), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarInt(), buf.readVarInt(), buf.readUtf(128), readBlocks(buf));
            }
            return new ViewPayload(recipes, inspection);
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
