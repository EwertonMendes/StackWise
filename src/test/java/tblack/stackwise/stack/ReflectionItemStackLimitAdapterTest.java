package tblack.stackwise.stack;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReflectionItemStackLimitAdapterTest {
    @Test
    void readsAndWritesSupportedItemShapeWithoutStartingHytale() throws ReflectiveOperationException {
        TestItem item = new TestItem();
        ReflectionItemStackLimitAdapter adapter = new ReflectionItemStackLimitAdapter(TestItem.class);

        assertEquals(1, adapter.read(item));
        adapter.write(item, 250);

        assertEquals(250, adapter.read(item));
        assertNull(item.cachedPacket);
        assertTrue(adapter.isAvailable());
    }

    @Test
    void oneResolutionSupportsDifferentConcreteSubclasses() throws ReflectiveOperationException {
        ReflectionItemStackLimitAdapter adapter = new ReflectionItemStackLimitAdapter(TestItem.class);
        FirstItem first = new FirstItem();
        SecondItem second = new SecondItem();

        adapter.write(first, 100);
        adapter.write(second, 200);

        assertEquals(100, adapter.read(first));
        assertEquals(200, adapter.read(second));
        assertNull(((TestItem) first).cachedPacket);
        assertNull(((TestItem) second).cachedPacket);
    }

    @Test
    void reportsTheResolvedAccessorAndCacheInvalidator() {
        ReflectionItemStackLimitAdapter adapter = new ReflectionItemStackLimitAdapter(TestItem.class);

        assertTrue(adapter.description().contains("maxStack"));
        assertTrue(adapter.description().contains("invalidatePacketCache"));
    }

    private static class TestItem {
        private int maxStack = 1;
        private Object cachedPacket = new Object();

        private int getMaxStack() {
            return maxStack;
        }

        private void invalidatePacketCache() {
            cachedPacket = null;
        }
    }

    private static final class FirstItem extends TestItem {
    }

    private static final class SecondItem extends TestItem {
    }
}
