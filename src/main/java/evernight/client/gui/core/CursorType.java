package evernight.client.gui.core;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.function.Supplier;

public enum CursorType {
    STANDARD(() -> GLFW.glfwCreateStandardCursor(GLFW.GLFW_ARROW_CURSOR)),
    POINTER(() -> GLFW.glfwCreateStandardCursor(GLFW.GLFW_HAND_CURSOR)),
    INPUT(() -> GLFW.glfwCreateStandardCursor(GLFW.GLFW_IBEAM_CURSOR));
    private static CursorType currentCursor = STANDARD;
    private final Supplier cursorSupplier;
    private long cursorHandle;

    CursorType(Supplier cursorSupplier) {
        this.cursorHandle = -1L;
        this.cursorSupplier = cursorSupplier;
    }

    public long getHandle() {
        long cachedHandle = this.cursorHandle;
        if (cachedHandle == -1L) {
            Long createdHandle = (Long) this.cursorSupplier.get();
            this.cursorHandle = createdHandle;
        }
        return this.cursorHandle;
    }

    public void apply() {
        if (this != currentCursor) {
            Minecraft minecraft = Minecraft.getInstance();
            Window window = minecraft.getWindow();
            long windowHandle = window.handle();
            long newCursorHandle = this.getHandle();
            long targetWindowHandle = windowHandle;
            GLFW.glfwSetCursor(targetWindowHandle, newCursorHandle);
            currentCursor = this;
        }
    }
}