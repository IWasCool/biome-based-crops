package com.ourcreation.biomebasedcrops;

import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;

/** Resolves physical world queries to actual room air in a moving structure's storage plot. */
public final class ShipGreenhouseQueries {
    private ShipGreenhouseQueries() { }

    public static BlockPos roomPosition(Level level, BlockPos position) {
        return resolve(level, position, true, false);
    }

    /** Routine world queries can resolve only rooms that have already been discovered. */
    static BlockPos knownRoomPosition(Level level, BlockPos position) {
        if (!level.isClientSide && !GreenhouseRoomScanner.hasCompletedShipRooms(level)) return position;
        return resolve(level, position, false, false);
    }

    /** Resolve the player's actual position before rounding to a cell in the ship's own axes. */
    static BlockPos knownRoomPosition(ServerPlayer player) {
        Level level = player.level();
        BlockPos fallback = player.blockPosition();
        if (!GreenhouseRoomScanner.hasCompletedShipRooms(level) || GreenhouseEnvironment.nativeQueryActive()
                || !GreenhouseSettings.enabled()) return fallback;
        var companion = SableCompanion.INSTANCE;
        if (companion.getContaining(level, fallback) != null) return fallback;
        Vector3d point = new Vector3d(player.getX(), player.getY(), player.getZ());
        var tracked = companion.getTrackingOrVehicleSubLevel(player);
        BlockPos local = interiorForPlayer(level, tracked, point);
        if (local != null) return local;
        var bounds = new BoundingBox3d(point.x - 0.001, point.y - 0.001, point.z - 0.001,
                point.x + 0.001, point.y + 0.001, point.z + 0.001);
        for (var ship : companion.getAllIntersecting(level, bounds)) {
            if (ship == tracked) continue;
            local = interiorForPlayer(level, ship, point);
            if (local != null) return local;
        }
        return fallback;
    }

    private static BlockPos interiorForPlayer(Level level, SubLevelAccess ship, Vector3d point) {
        if (ship == null) return null;
        Vector3d local = ship.logicalPose().transformPositionInverse(new Vector3d(point));
        if (!Double.isFinite(local.x) || !Double.isFinite(local.y) || !Double.isFinite(local.z)) return null;
        // Feet standing on the room floor can land just below its top due to transform rounding.
        BlockPos cell = BlockPos.containing(local.x, local.y + 0.0001D, local.z);
        var room = GreenhouseRoomScanner.completedAt(level, cell);
        return room != null && ship.getUniqueId().equals(room.key.spaceId()) ? cell : null;
    }

    /** Explicit notebook surveys may follow a pending local scan before the room is confirmed. */
    static BlockPos surveyPosition(Level level, BlockPos position) {
        return resolve(level, position, true, true);
    }

    private static BlockPos resolve(Level level, BlockPos position, boolean discover, boolean survey) {
        if (GreenhouseEnvironment.nativeQueryActive() || !GreenhouseSettings.enabled()
                || !level.isClientSide && level.getServer() != null && !level.getServer().isSameThread()) return position;
        var companion = SableCompanion.INSTANCE;
        if (companion.getContaining(level, position) != null) return position;
        if (level.isClientSide) return GreenhouseClientBiomes.localPositionAt(level, position);
        double x = position.getX() + 0.5, y = position.getY() + 0.5, z = position.getZ() + 0.5;
        var bounds = new BoundingBox3d(x - 0.001, y - 0.001, z - 0.001, x + 0.001, y + 0.001, z + 0.001);
        BlockPos pending = null;
        for (var ship : companion.getAllIntersecting(level, bounds)) {
            Vector3d local = ship.logicalPose().transformPositionInverse(new Vector3d(x, y, z));
            if (!Double.isFinite(local.x) || !Double.isFinite(local.y) || !Double.isFinite(local.z)) continue;
            BlockPos candidate = BlockPos.containing(local.x, local.y, local.z);
            var containing = companion.getContaining(level, candidate);
            if (containing == null || !ship.getUniqueId().equals(containing.getUniqueId())
                    || !GreenhouseEnvironment.loaded(level, candidate)) continue;
            var geometry = GreenhouseRoomScanner.completedAt(level, candidate);
            if (geometry == null && discover && !GreenhouseEnvironment.sealed(level, candidate)) {
                if (survey) GreenhouseRoomScanner.prioritize(level, candidate);
                geometry = survey ? GreenhouseRoomScanner.findForControllerInspection(level, candidate)
                        : GreenhouseRoomScanner.find(level, candidate);
            }
            if (geometry == null) continue;
            if (geometry.live && geometry.ready && geometry.spaceMatches(level)
                    && geometry.interior.contains(candidate.asLong())) return candidate;
            if (survey && geometry.status.equals("scanning") && pending == null) pending = candidate;
        }
        return pending == null ? position : pending;
    }
}
