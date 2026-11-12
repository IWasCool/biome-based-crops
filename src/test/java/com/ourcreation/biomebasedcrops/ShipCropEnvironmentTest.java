package com.ourcreation.biomebasedcrops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.impl.DefaultSableCompanion;
import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.core.BlockPos;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ShipCropEnvironmentTest {
    private static ClassLoader originalContextLoader;

    @BeforeAll
    static void useTheModLoaderForCompanionServices() {
        // ModDev's JUnit worker also has the dependency on Gradle's parent classpath.
        // Resolve ServiceLoader providers through the same transformed loader as the API,
        // matching the game launch, instead of loading a second copy through that parent.
        Thread worker = Thread.currentThread();
        originalContextLoader = worker.getContextClassLoader();
        worker.setContextClassLoader(SableCompanion.class.getClassLoader());
    }

    @AfterAll
    static void restoreTheJUnitWorkerLoader() {
        Thread.currentThread().setContextClassLoader(originalContextLoader);
    }

    @Test
    void companionWithoutSableLeavesOrdinaryBlocksAtTheirWorldPositions() {
        var companion = new DefaultSableCompanion();
        var position = new BlockPos(-17, 65, 31);

        assertSame(position, ShipCropEnvironment.worldPosition(null, position, companion));
        assertFalse(companion.isInPlotGrid(null, position));
        assertTrue(ShipCropEnvironment.sameSpace(null, position, position.east(), companion));
    }

    @Test
    void ordinaryWorldLookupSkipsProjectionAndSnapshotsMutablePositions() {
        SableCompanion companion = (SableCompanion) Proxy.newProxyInstance(SableCompanion.class.getClassLoader(),
                new Class<?>[]{SableCompanion.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("getContaining")) return null;
                    throw new AssertionError("Ordinary world positions must not perform ship projection");
                });
        var position = new BlockPos.MutableBlockPos(15, 65, -17);
        BlockPos world = ShipCropEnvironment.worldPosition(null, position, companion);
        position.set(100, 70, 100);

        assertEquals(new BlockPos(15, 65, -17), world);
    }

    @Test
    void nearbyStoragePlotsDoNotShareControllerCoverage() {
        BlockPos first = new BlockPos(30_000_000, 64, 30_000_000);
        BlockPos neighbor = first.east();
        BlockPos other = first.north();
        BlockPos world = new BlockPos(10, 64, 10);
        UUID firstId = UUID.randomUUID();
        Map<BlockPos, SubLevelAccess> ships = Map.of(first, ship(firstId), neighbor, ship(firstId), other, ship(UUID.randomUUID()));
        SableCompanion companion = (SableCompanion) Proxy.newProxyInstance(SableCompanion.class.getClassLoader(),
                new Class<?>[]{SableCompanion.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("getContaining") && arguments.length == 2)
                        return ships.get(arguments[1]);
                    throw new UnsupportedOperationException(method.toString());
                });

        assertTrue(ShipCropEnvironment.sameSpace(null, first, neighbor, companion));
        assertFalse(ShipCropEnvironment.sameSpace(null, first, other, companion));
        assertFalse(ShipCropEnvironment.sameSpace(null, first, world, companion));
        assertFalse(ShipCropEnvironment.sameSpace(null, world, first, companion));
        assertTrue(ShipCropEnvironment.sameSpace(null, world, world.east(), companion));
    }

    @Test
    void movingAcrossBiomeBoundariesReadsTheCurrentPoseEveryTime() {
        var plot = new BlockPos(30_000_010, 70, 30_000_020);
        var pose = new Pose3d();
        pose.rotationPoint().set(plot.getX() + 0.5, plot.getY() + 0.5, plot.getZ() + 0.5);
        pose.position().set(15.75, 70.5, -0.25);
        var projections = new AtomicInteger();
        SableCompanion companion = projectedBy(pose, projections);

        assertEquals(new BlockPos(15, 70, -1), ShipCropEnvironment.worldPosition(null, plot, companion));
        pose.position().set(16.25, 70.5, 0.25);
        assertEquals(new BlockPos(16, 70, 0), ShipCropEnvironment.worldPosition(null, plot, companion));
        assertEquals(2, projections.get(), "Ship position must not be cached between biome checks");
        assertEquals(new BlockPos(30_000_010, 70, 30_000_020), plot,
                "Biome sampling must not replace the block position used for crop/controller lookup");
    }

    @Test
    void rotationProjectsEachCropCenterInsteadOfTheShipAnchor() {
        var plotOrigin = new BlockPos(30_000_000, 64, 30_000_000);
        var pose = new Pose3d();
        pose.rotationPoint().set(plotOrigin.getX() + 0.5, plotOrigin.getY() + 0.5, plotOrigin.getZ() + 0.5);
        pose.position().set(100.5, 64.5, 200.5);
        pose.orientation().rotateY(Math.PI / 2);
        var companion = projectedBy(pose, new AtomicInteger());

        assertEquals(new BlockPos(100, 64, 200), ShipCropEnvironment.worldPosition(null, plotOrigin, companion));
        assertEquals(new BlockPos(100, 64, 198), ShipCropEnvironment.worldPosition(null, plotOrigin.east(2), companion));
        assertEquals(new BlockPos(102, 64, 200), ShipCropEnvironment.worldPosition(null, plotOrigin.south(2), companion));
    }

    private static SableCompanion projectedBy(Pose3d pose, AtomicInteger projections) {
        SubLevelAccess containingShip = ship(UUID.randomUUID());
        return (SableCompanion) Proxy.newProxyInstance(SableCompanion.class.getClassLoader(),
                new Class<?>[]{SableCompanion.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("getContaining")) return containingShip;
                    if (method.getName().equals("projectOutOfSubLevel") && arguments.length == 3) {
                        projections.incrementAndGet();
                        return pose.transformPosition((Vector3dc) arguments[1], (Vector3d) arguments[2]);
                    }
                    throw new UnsupportedOperationException(method.toString());
                });
    }

    private static SubLevelAccess ship(UUID id) {
        return (SubLevelAccess) Proxy.newProxyInstance(SubLevelAccess.class.getClassLoader(),
                new Class<?>[]{SubLevelAccess.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("getUniqueId")) return id;
                    throw new UnsupportedOperationException(method.toString());
                });
    }
}
