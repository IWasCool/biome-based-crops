package com.ourcreation.biomebasedcrops;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
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

/** Sends the field guide's biome catalogue and automatic room-size context. */
public final class GreenhouseNetwork {
    private static final Map<ServerPlayer, PendingGuide> PENDING_GUIDES = new WeakHashMap<>();

    private GreenhouseNetwork() { }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("9").playToClient(ViewPayload.TYPE, ViewPayload.CODEC,
                (payload, context) -> com.ourcreation.biomebasedcrops.client.GreenhouseViews.open(payload));
    }

    public static void showGuide(ServerPlayer player) {
        PENDING_GUIDES.remove(player);
        BlockPos position = player.blockPosition();
        GreenhouseRoomScanner.prioritize(player.level(), position);
        var room = GreenhouseEnvironment.inspect(player.level(), position);
        if (room.status().endsWith(".scanning")) {
            PENDING_GUIDES.put(player, new PendingGuide(position.immutable(), player.level().dimension().location(),
                    player.level().getGameTime() + 6000));
        }
        send(player, false, room.roomVolume(), room.floorArea());
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 10 != 0) return;
        var pending = PENDING_GUIDES.get(player);
        if (pending == null) return;
        if (player.level().getGameTime() > pending.expires()
                || !player.level().dimension().location().equals(pending.dimension())) {
            PENDING_GUIDES.remove(player);
            return;
        }
        var room = GreenhouseEnvironment.inspect(player.level(), pending.position());
        if (!room.status().endsWith(".scanning")) {
            PENDING_GUIDES.remove(player);
            send(player, true, room.roomVolume(), room.floorArea());
        }
    }

    private static void send(ServerPlayer player, boolean updateOnly, int contextVolume, int contextFloorArea) {
        Map<ResourceLocation, GreenhouseRecipes.Recipe> recipes = GreenhouseRecipes.serverCatalog(
                player.registryAccess().registryOrThrow(Registries.BIOME));
        PacketDistributor.sendToPlayer(player, new ViewPayload(recipes, updateOnly,
                GreenhouseSettings.materialReferenceVolume(), contextVolume, contextFloorArea));
    }

    private record PendingGuide(BlockPos position, ResourceLocation dimension, long expires) { }

    public record ViewPayload(Map<ResourceLocation, GreenhouseRecipes.Recipe> recipes, boolean updateOnly,
                              int referenceVolume, int contextVolume, int contextFloorArea) implements CustomPacketPayload {
        public static final Type<ViewPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(BiomeBasedCrops.MODID, "greenhouse_view"));
        public static final StreamCodec<FriendlyByteBuf, ViewPayload> CODEC = StreamCodec.of(ViewPayload::write, ViewPayload::read);

        public ViewPayload {
            recipes = Map.copyOf(recipes);
            if (referenceVolume < 64 || referenceVolume > 1048576 || contextVolume < 0 || contextVolume > 1048576
                    || contextFloorArea < 0 || contextFloorArea > 1048576)
                throw new IllegalArgumentException("Invalid greenhouse volume context");
        }

        public ViewPayload(Map<ResourceLocation, GreenhouseRecipes.Recipe> recipes) {
            this(recipes, false, GreenhouseSettings.materialReferenceVolume(), 0, 0);
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
            return new ViewPayload(recipes, buf.readBoolean(), bounded(buf.readVarInt(), 1_048_576),
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
