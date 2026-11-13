package com.ourcreation.biomebasedcrops;

import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;

/** Uses the real old/new NeoForge energy API, including transactional rollback. */
@ExtendWith(EphemeralTestServerProvider.class)
public class ControllerEnergySmokeTest {
    @Test
    void energyTransferRespectsSimulationLimitsAndCapacity(MinecraftServer server) throws ReflectiveOperationException {
        assertNotNull(server.registryAccess());
        AirConditionerBlockEntity controller = new AirConditionerBlockEntity(BlockPos.ZERO,
                GreenhouseContent.AIR_CONDITIONER.get().defaultBlockState());
        Object storage = controller.energyStorage();
        EnergyAccess energy = access(storage);
        int maxReceive = AirConditionerBlockEntity.MAX_RECEIVE;

        assertEquals(maxReceive, energy.insert(maxReceive + 1, false));
        assertEquals(0, controller.energyStored(), "Simulation or aborted transactions must not store energy");

        assertEquals(maxReceive, energy.insert(maxReceive + 1, true));
        assertEquals(maxReceive, controller.energyStored());
        assertEquals(0, energy.extract(1));
        assertEquals(maxReceive, controller.energyStored(), "The controller must not export its stored energy");

        assertEquals(123, energy.insert(123, false));
        assertEquals(maxReceive, controller.energyStored(), "Aborting another transfer must preserve the existing balance");

        for (int attempts = 0; controller.energyStored() < AirConditionerBlockEntity.CAPACITY && attempts < 100; attempts++) {
            int previous = controller.energyStored();
            int expected = Math.min(maxReceive, AirConditionerBlockEntity.CAPACITY - previous);
            assertEquals(expected, energy.insert(Integer.MAX_VALUE, true));
            assertEquals(previous + expected, controller.energyStored());
        }
        assertEquals(AirConditionerBlockEntity.CAPACITY, controller.energyStored());
        assertEquals(0, energy.insert(1, true));
        assertEquals(0, energy.insert(1, false));
        assertEquals(0, energy.extract(Integer.MAX_VALUE));
        assertEquals(AirConditionerBlockEntity.CAPACITY, controller.energyStored());
    }

    private static EnergyAccess access(Object storage) throws ReflectiveOperationException {
        try {
            Class<?> legacy = Class.forName("net.neoforged.neoforge.energy.IEnergyStorage");
            if (legacy.isInstance(storage)) {
                Method receive = legacy.getMethod("receiveEnergy", int.class, boolean.class);
                Method extract = legacy.getMethod("extractEnergy", int.class, boolean.class);
                return new EnergyAccess() {
                    @Override public int insert(int amount, boolean commit) throws ReflectiveOperationException {
                        return ((Number) receive.invoke(storage, amount, !commit)).intValue();
                    }
                    @Override public int extract(int amount) throws ReflectiveOperationException {
                        return ((Number) extract.invoke(storage, amount, false)).intValue();
                    }
                };
            }
        } catch (ClassNotFoundException ignored) {
            // NeoForge 21.9 replaces IEnergyStorage with the transactional transfer API.
        }

        Class<?> handler = Class.forName("net.neoforged.neoforge.transfer.energy.EnergyHandler");
        Class<?> transaction = Class.forName("net.neoforged.neoforge.transfer.transaction.Transaction");
        Class<?> context = Class.forName("net.neoforged.neoforge.transfer.transaction.TransactionContext");
        assertTrue(handler.isInstance(storage));
        Method insert = handler.getMethod("insert", int.class, context);
        Method extract = handler.getMethod("extract", int.class, context);
        Method open;
        try {
            open = transaction.getMethod("openRoot");
        } catch (NoSuchMethodException ignored) {
            open = transaction.getMethod("open", context);
        }
        Method openTransaction = open;
        Method commitTransaction = transaction.getMethod("commit");
        Method closeTransaction = transaction.getMethod("close");
        return new EnergyAccess() {
            private int transfer(Method operation, int amount, boolean commit) throws ReflectiveOperationException {
                Object active = openTransaction.getParameterCount() == 0
                        ? openTransaction.invoke(null) : openTransaction.invoke(null, new Object[]{null});
                try {
                    int accepted = ((Number) operation.invoke(storage, amount, active)).intValue();
                    if (commit) commitTransaction.invoke(active);
                    return accepted;
                } finally {
                    closeTransaction.invoke(active);
                }
            }
            @Override public int insert(int amount, boolean commit) throws ReflectiveOperationException {
                return transfer(insert, amount, commit);
            }
            @Override public int extract(int amount) throws ReflectiveOperationException {
                return transfer(extract, amount, true);
            }
        };
    }

    private interface EnergyAccess {
        int insert(int amount, boolean commit) throws ReflectiveOperationException;
        int extract(int amount) throws ReflectiveOperationException;
    }
}
