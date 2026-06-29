package tblack.stackwise.stack;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReflectionItemStackLimitAdapterTest {
    @Test
    void readsAndWritesSupportedItemShapeWithoutStartingHytale() throws ReflectiveOperationException {
        TestItem item = new TestItem();
        ReflectionItemStackLimitAdapter adapter = new ReflectionItemStackLimitAdapter(TestItem.class);

        assertEquals(1, adapter.read(item));
        adapter.write(item, 250);

        assertEquals(250, adapter.read(item));
        assertEquals(null, item.cachedPacket);
        assertTrue(adapter.isAvailable());
    }

    @Test
    void reportsTheResolvedAccessorAndCacheInvalidator() {
        ReflectionItemStackLimitAdapter adapter = new ReflectionItemStackLimitAdapter(TestItem.class);

        assertTrue(adapter.description().contains("maxStack"));
        assertTrue(adapter.description().contains("invalidatePacketCache"));
    }

    private static final class TestItem {
        private int maxStack = 1;
        private Object cachedPacket = new Object();

        private int getMaxStack() {
            return maxStack;
        }

        private void invalidatePacketCache() {
            cachedPacket = null;
        }
    }
}
