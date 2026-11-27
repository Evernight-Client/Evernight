package evernight.client.utils.math;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloatBoundsTest {
    private static final FloatBounds BASE = new FloatBounds(0.0F, 0.0F, 10.0F, 10.0F);

    @Test
    @DisplayName("位置+尺寸得到 left/top/right/bottom")
    void fromPositionAndSize() {
        FloatBounds bounds = FloatBounds.fromPositionAndSize(10.0F, 20.0F, 30.0F, 40.0F);
        assertEquals(10.0F, bounds.minX());
        assertEquals(20.0F, bounds.minY());
        assertEquals(40.0F, bounds.maxX());
        assertEquals(60.0F, bounds.maxY());
        assertEquals(30.0F, bounds.getWidth());
        assertEquals(40.0F, bounds.getHeight());
    }

    @Test
    @DisplayName("expand 单值/双值扩展四边")
    void expand() {
        FloatBounds expanded = BASE.expand(2.0F);
        assertEquals(new FloatBounds(-2.0F, -2.0F, 12.0F, 12.0F), expanded);
        assertEquals(new FloatBounds(-2.0F, -3.0F, 12.0F, 13.0F),
                BASE.expand(2.0F, 3.0F));
    }

    @Test
    @DisplayName("union 覆盖所有矩形，空 varargs 保持自身")
    void union() {
        FloatBounds other = new FloatBounds(5.0F, 5.0F, 15.0F, 15.0F);
        FloatBounds third = new FloatBounds(-5.0F, 20.0F, 0.0F, 30.0F);
        assertEquals(new FloatBounds(-5.0F, 0.0F, 15.0F, 30.0F),
                BASE.union(other, third));
        assertEquals(BASE, BASE.union());
    }

    @Test
    @DisplayName("contains 包含边界")
    void contains() {
        assertTrue(BASE.contains(0.0, 0.0));
        assertTrue(BASE.contains(10.0, 10.0));
        assertTrue(BASE.contains(5.0, 5.0));
        assertFalse(BASE.contains(10.1, 5.0));
        assertFalse(BASE.contains(5.0, -0.1));
    }

    @Test
    @DisplayName("intersects 判断重叠/包含/相切/分离")
    void intersects() {
        assertTrue(BASE.intersects(new FloatBounds(5.0F, 5.0F, 15.0F, 15.0F)));
        assertTrue(BASE.intersects(new FloatBounds(2.0F, 2.0F, 8.0F, 8.0F)));
        assertFalse(BASE.intersects(new FloatBounds(10.0F, 0.0F, 20.0F, 10.0F)),
                "touching edges do not overlap");
        assertFalse(BASE.intersects(new FloatBounds(11.0F, 0.0F, 20.0F, 10.0F)));
        assertFalse(BASE.intersects(new FloatBounds(0.0F, 11.0F, 10.0F, 20.0F)));
    }
}
